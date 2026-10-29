# ============================================================
# M5 补测：超卖修复后多轮复验
# 不改 run-stress.ps1，不覆盖 result 下任何原始 evidence
# 用法：.\run-stress-loop.ps1 -Loops 50
# ============================================================
param(
    [int]$Loops = 50,
    [int]$Users = 9,
    [int]$Stock = 5,
    [int]$SkuId = 112,
    [string]$JmeterHome = 'F:/develop/JMeter/apache-jmeter-5.6.3',
    [string]$StressDir = 'F:/develop/mall-work/stress',
    [string]$Host_ = '127.0.0.1',
    [int]$Port = 8092,
    [string]$Mysql = 'C:/Program Files\MySQL\MySQL Server 8.1\bin\mysql.exe',
    [string]$DbUser = 'root',
    [string]$DbPass = '123456',
    [string]$DbName = 'mall',
    [string]$Plan = ''
)

$ErrorActionPreference = 'Continue'

$JM = Join-Path $JmeterHome 'bin\jmeter.bat'
if ($Plan -ne '') { $plan = $Plan } else { $plan = Join-Path $StressDir 'plan\order-seckill.jmx' }
$resultDir = Join-Path $StressDir 'result'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$csv = Join-Path $resultDir "loop-after-$stamp.csv"
$members = '1,3,4,5,6,7,8,9,13'

function Sql([string]$q) {
    (& $Mysql '--host=127.0.0.1' "--user=$DbUser" "--password=$DbPass" "--database=$DbName" '--batch' '--skip-column-names' -e $q 2>&1) | Where-Object { $_ -notmatch 'Using a password' -and $_ -notmatch '^mysql:' }
}
function SqlFile([string]$f) {
    (Get-Content $f -Raw | & $Mysql '--host=127.0.0.1' "--user=$DbUser" "--password=$DbPass" "--database=$DbName" '--batch' '--skip-column-names' 2>&1) | Where-Object { $_ -notmatch 'Using a password' -and $_ -notmatch '^mysql:' }
}

Write-Host '========== 补测前置检查 ==========' -ForegroundColor Cyan

if (-not (Test-Path $JM))   { Write-Host "  x 找不到 JMeter: $JM" -ForegroundColor Red; exit 1 }
if (-not (Test-Path $plan)) { Write-Host "  x 找不到测试计划: $plan" -ForegroundColor Red; exit 1 }
foreach ($p in @($Port, 8401)) {
    $up = Test-NetConnection -ComputerName 127.0.0.1 -Port $p -InformationLevel Quiet -WarningAction SilentlyContinue
    Write-Host "  端口 $p -> $(if($up){'UP'}else{'DOWN'})" -ForegroundColor $(if($up){'Green'}else{'Red'})
    if (-not $up) { Write-Host '  x 后端未就绪' -ForegroundColor Red; exit 1 }
}
if ($Users -le $Stock) {
    Write-Host "  x 无效场景：并发($Users) <= 可售($Stock)" -ForegroundColor Red; exit 1
}
$skuCnt = (Sql "SELECT COUNT(*) FROM pms_sku_stock WHERE id=$SkuId;").Trim()
if ($skuCnt -ne '1') { Write-Host "  x SKU $SkuId 不存在" -ForegroundColor Red; exit 1 }
$memCnt = (Sql "SELECT COUNT(*) FROM ums_member WHERE id IN ($members);").Trim()
if ($memCnt -ne '9') { Write-Host "  x 会员数异常: $memCnt (期望 9)" -ForegroundColor Red; exit 1 }
Write-Host "  场景校验：并发 $Users > 可售 $Stock  OK" -ForegroundColor Green
Write-Host "  轮数：$Loops   结果：$csv" -ForegroundColor Gray

'round,success_orders,stock,lock_stock,locked_by_orders,verdict' | Set-Content $csv -Encoding UTF8

$cartSql = "INSERT INTO oms_cart_item (product_id, product_sku_id, member_id, quantity, price, product_pic, product_name, product_sku_code, member_nickname, create_date, delete_status, product_category_id, product_brand, product_sn, product_attr) SELECT s.product_id, s.id, mu.id, 1, s.price, IFNULL(p.pic,''), p.name, s.sku_code, mu.username, NOW(), 0, p.product_category_id, p.brand_name, p.product_sn, '' FROM pms_sku_stock s JOIN pms_product p ON p.id=s.product_id JOIN (SELECT id, username FROM ums_member WHERE id IN ($members)) mu WHERE s.id=$SkuId;"

$csvSql = "SELECT CONCAT(m.username, ',', IFNULL(c.id,0), ',', IFNULL((SELECT id FROM ums_member_receive_address a WHERE a.member_id=m.id ORDER BY a.default_status DESC, a.id LIMIT 1),0)) FROM (SELECT 1 id UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 13) ids JOIN ums_member m ON m.id=ids.id LEFT JOIN oms_cart_item c ON c.member_id=m.id AND c.product_sku_id=$SkuId ORDER BY m.id;"

$okCount = 0
$failRounds = @()

for ($i = 1; $i -le $Loops; $i++) {
    Sql "UPDATE pms_sku_stock SET stock=$Stock, lock_stock=0 WHERE id=$SkuId;" | Out-Null
    Sql "DELETE FROM oms_order_item WHERE order_id IN (SELECT id FROM oms_order WHERE member_id IN ($members));" | Out-Null
    Sql "DELETE FROM oms_order WHERE member_id IN ($members);" | Out-Null
    Sql "DELETE FROM oms_cart_item WHERE member_id IN ($members);" | Out-Null
    SqlFile (Join-Path $StressDir 'scripts\02-seed-address.sql') | Out-Null
    Sql $cartSql | Out-Null

    $csvPath = Join-Path $StressDir 'data\members.csv'
    'member,cartId,addressId' | Set-Content $csvPath -Encoding UTF8
    $rows = Sql $csvSql
    $rows | Where-Object { $_ -match ',' } | Add-Content $csvPath -Encoding UTF8

    $jtl = Join-Path $resultDir ("loop-after-$stamp-r{0}.jtl" -f $i)
    & $JM -n -t $plan -l $jtl "-Jhost=$Host_" "-Jport=$Port" "-Jusers=$Users" "-Jloops=1" "-Jdatadir=$StressDir\data" "-Jjmeter.save.saveservice.response_data=true" *> $null

    $success = (Sql "SELECT COUNT(*) FROM oms_order WHERE member_id IN ($members) AND status=0 AND delete_status=0;").Trim()
    $sl = (Sql "SELECT CONCAT(stock,'|',lock_stock) FROM pms_sku_stock WHERE id=$SkuId;").Trim()
    $items = (Sql "SELECT IFNULL(SUM(oi.product_quantity),0) FROM oms_order_item oi JOIN oms_order o ON o.id=oi.order_id WHERE oi.product_sku_id=$SkuId AND o.status=0 AND o.delete_status=0;").Trim()
    $parts = $sl -split '\|'
    $stockNow = $parts[0]
    $lockNow = $parts[1]

    $verdict = 'FAIL'
    if ($success -eq "$Stock" -and $lockNow -eq "$Stock" -and $items -eq "$Stock") { $verdict = 'PASS'; $okCount++ }
    else { $failRounds += $i }

    "$i,$success,$stockNow,$lockNow,$items,$verdict" | Add-Content $csv -Encoding UTF8
    $color = if ($verdict -eq 'PASS') { 'Green' } else { 'Red' }
    Write-Host ("  [轮 {0,3}/{1}] 成功={2} stock={3} lock_stock={4} 明细合计={5}  {6}" -f $i, $Loops, $success, $stockNow, $lockNow, $items, $verdict) -ForegroundColor $color
}

Write-Host ''
Write-Host '========== 汇总 ==========' -ForegroundColor Cyan
Write-Host "  总轮数 : $Loops"
Write-Host "  通过   : $okCount" -ForegroundColor Green
Write-Host "  失败   : $($Loops - $okCount)" -ForegroundColor $(if ($okCount -eq $Loops) { 'Green' } else { 'Red' })
if ($failRounds.Count -gt 0) { Write-Host "  失败轮次: $($failRounds -join ',')" -ForegroundColor Red }
$dist = Import-Csv $csv | Group-Object success_orders | ForEach-Object { "成功 $($_.Name) 单 x $($_.Count) 轮" }
Write-Host "  成功数分布: $($dist -join ' / ')"
Write-Host "  CSV: $csv" -ForegroundColor Gray
