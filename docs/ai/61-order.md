# 61 订单域（my-mall-order，8089）— 5 Controller / 21 端点

## 归属与数据
- 表：oms_* 共 7 张（不含 oms_cart_item，购物车属门户域）
- 手写 DAO：OmsOrderDao、OmsOrderOperateHistoryDao、OmsOrderReturnApplyDao
- DTO 全部在 domain/：OmsOrderQueryParam、OmsOrderDetail、OmsOrderDeliveryParam、OmsReceiverInfoParam、OmsMoneyInfoParam、OmsReturnApplyQueryParam、OmsOrderReturnApplyResult、OmsUpdateStatusParam

## ★ 类前缀
/order、/returnApply、/returnReason、/orderSetting、/companyAddress

## ★ 三条硬规则（踩过坑）
1. DTO 必须在 domain/：两个手写 XML 用 com.mymall.order.domain.* 引用它们；DTO 若放 model/，MyBatis 解析 XML 时 ClassNotFoundException → 启动即失败
2. OmsOrderDao.getList 与 OmsOrderReturnApplyDao.getList 必须带 @Param("queryParam")：XML 用 #{queryParam.xxx}，缺注解会报 There is no getter for property named queryParam → 500
3. list 系列前面必须有 PageHelper.startPage

## ★ 有意差异（保留）
/order/{id}（getDetail）与原实现设计不同：
- 原实现：一条三表 JOIN（oms_order + oms_order_item + oms_order_operate_history）+ collection 映射 + ORDER BY oi.id ASC, oh.create_time DESC
- 本实现：只查主表，明细/操作记录在 Service 层另查（避免 JOIN 笛卡尔积）
- 必须保持：orderItemList 按 id ASC、historyList 按 create_time DESC；无数据时 set 空 list（[]）而非 null，否则响应与老服务不一致

## 验收状态（2026-10）
- 路由清单 21/21 一致；只读端点 8/8 逐字节一致
- 写端点（发货/关闭/删除/备注/收货人/费用）：见 verify-write-d7.ps1