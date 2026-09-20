<#
  修复 IDEA 报错：java: 没有为模块 'my-mall-common-core' 指定 JDK

  原理：IDEA 的项目级 SDK 配置在 .idea/misc.xml 的 ProjectRootManager 里。
       当前 .idea 是半成品（只有 workspace.xml），说明 Maven 导入没跑完 → 模块没有 SDK。
       本脚本重建一份正确的 .idea 配置（项目 SDK=17、语言级别=17、字节码=17、Maven 根 pom、导入 JDK=17）。

  用法：
     1) 先【完全退出 IDEA】（File → Exit，不是关窗口）——否则 IDEA 退出时会覆盖本脚本写入的文件
     2) powershell -ExecutionPolicy Bypass -File .\fix-idea-config.ps1
     3) 启动 IDEA → File → Open → 选本目录（或直接选 pom.xml）→ 等 Maven 导入完成
#>
param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$ideaDir = Join-Path $root '.idea'
$jdkName = '17'          # 与 IDEA 里注册的 SDK 名称一致（jdk.table.xml 中 name=17）
$level = '17'

$running = Get-Process -Name 'idea64', 'idea' -ErrorAction SilentlyContinue
if ($running -and -not $Force) {
    Write-Host "!! IDEA 正在运行 (PID: $($running.Id -join ', '))" -ForegroundColor Red
    Write-Host "   请先完全退出 IDEA（File -> Exit），否则它退出时会覆盖本脚本写入的配置。" -ForegroundColor Yellow
    Write-Host "   确认已退出后可执行： .\fix-idea-config.ps1 -Force" -ForegroundColor Yellow
    exit 1
}

$enc = New-Object System.Text.UTF8Encoding($false)
if (Test-Path $ideaDir) { Remove-Item $ideaDir -Recurse -Force }
New-Item -ItemType Directory -Force $ideaDir | Out-Null

$modules = @('my-mall', 'my-mall-common', 'my-mall-common-core', 'my-mall-common-redis',
             'my-mall-common-web', 'my-mall-api', 'my-mall-gateway')

$misc = @"
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ExternalStorageConfigurationManager" enabled="true" />
  <component name="MavenProjectsManager">
    <option name="originalFiles">
      <list>
        <option value="`$PROJECT_DIR`$/pom.xml" />
      </list>
    </option>
  </component>
  <component name="MavenImportPreferences">
    <option name="generalSettings">
      <MavenGeneralSettings>
        <option name="jdkForImporter" value="$jdkName" />
      </MavenGeneralSettings>
    </option>
  </component>
  <component name="ProjectRootManager" version="2" languageLevel="JDK_$level" default="true" project-jdk-name="$jdkName" project-jdk-type="JavaSDK">
    <output url="file://`$PROJECT_DIR`$/out" />
  </component>
</project>
"@
[IO.File]::WriteAllText((Join-Path $ideaDir 'misc.xml'), $misc, $enc)

$moduleLines = ($modules | ForEach-Object { "      <module name=`"$_`" target=`"$level`" />" }) -join "`n"
$compiler = @"
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="CompilerConfiguration">
    <bytecodeTargetLevel target="$level">
$moduleLines
    </bytecodeTargetLevel>
  </component>
  <component name="JavacSettings">
    <option name="ADDITIONAL_OPTIONS_STRING" value="-parameters" />
  </component>
</project>
"@
[IO.File]::WriteAllText((Join-Path $ideaDir 'compiler.xml'), $compiler, $enc)

[IO.File]::WriteAllText((Join-Path $ideaDir 'encodings.xml'), @"
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="Encoding" defaultCharsetForPropertiesFiles="UTF-8">
    <file url="PROJECT" charset="UTF-8" />
  </component>
</project>
"@, $enc)

[IO.File]::WriteAllText((Join-Path $ideaDir 'vcs.xml'), @"
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="VcsDirectoryMappings">
    <mapping directory="`$PROJECT_DIR`$" vcs="Git" />
  </component>
</project>
"@, $enc)

[IO.File]::WriteAllText((Join-Path $ideaDir '.name'), "my-mall`n", $enc)

Write-Host "已重建 .idea 配置：" -ForegroundColor Green
Get-ChildItem $ideaDir -Force | ForEach-Object { Write-Host ("  {0,-16} {1,5:N0} B" -f $_.Name, $_.Length) }
Write-Host ""
Write-Host "下一步：" -ForegroundColor Cyan
Write-Host "  1) 启动 IDEA -> File -> Open -> 选 $root（或直接选 pom.xml）"
Write-Host "  2) 等右下角 Maven 导入进度条走完（会创建 7 个模块）"
Write-Host "  3) 若仍报错，检查 Settings -> Build Tools -> Maven -> Importing 的 JDK for importer 是否为 17"