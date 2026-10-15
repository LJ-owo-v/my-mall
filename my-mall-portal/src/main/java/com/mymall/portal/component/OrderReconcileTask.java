package com.mymall.portal.component;

import com.mymall.portal.domain.ReconcileResult;
import com.mymall.portal.service.OmsPortalOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据对账定时任务（M5 #12）
 *
 * 为什么需要它：
 *   已完成 #9/#14/#15A/#16 都是"防"（不让错误发生），但"防"有三个盲区 ——
 *     ① 防不住未知的 bug（只修了已知的 4 个）
 *     ② 防不住绕过代码的操作（DBA 手工改库、数据迁移、历史遗留）
 *     ③ 防不住"改代码时引入的新 bug"
 *   对账是唯一能覆盖这三点的"发现"机制。
 *
 * 为什么不能靠日志代替：
 *   日志是"点的、被动的、只覆盖代码走到过的地方"。
 *   例：#9 丢失更新时两个事务都成功提交、无异常、无回滚 —— 一行 warning 都不会有，
 *       只有"lock_stock=2 但订单明细合计=9"这个矛盾能被对账表达出来。
 *
 * 为什么必须现在补：
 *   ① 库里已确认还有未发现的问题（首次试跑 4 条不变量即命中 SKU 201 孤儿锁定）；
 *   ② 下一步要动架构（§八 拆库）—— 跨库必然产生不一致，没有对账等于蒙眼跳崖。
 *
 * 行为约定：
 *   · 每天凌晨 3 点执行（避开业务高峰）
 *   · 【只读】—— 不修改任何数据；自动修复风险高，需人工确认后再处理
 *   · 发现不一致用 log.error 并以 "★ 对账发现不一致" 开头，便于日志检索/告警接入
 */
@Component
public class OrderReconcileTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderReconcileTask.class);

    @Autowired
    private OmsPortalOrderService portalOrderService;

    /**
     * 每天凌晨 3:00 执行数据对账（避开业务高峰）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void reconcile() {
        LOGGER.info("开始数据对账（M5 #12）：检查 5 条业务不变量");

        List<ReconcileResult> results;
        try {
            results = portalOrderService.reconcile();
        } catch (Exception e) {
            // 对账本身失败也要留痕：否则"对账没跑"这件事会静默发生
            LOGGER.error("★ 数据对账执行失败！", e);
            return;
        }

        int failed = 0;
        for (ReconcileResult r : results) {
            if (r.isPassed()) {
                LOGGER.info("对账通过: {} {} 命中=0", r.getCode(), r.getName());
            } else {
                failed++;
                // 统一前缀，便于日志采集系统按关键字告警
                LOGGER.error("★ 对账发现不一致: 不变量={} {}, 命中={}, 明细={}",
                        r.getCode(), r.getName(), r.getHitCount(), r.getDetail());
            }
        }

        if (failed == 0) {
            LOGGER.info("数据对账完成：5 条不变量全部通过");
        } else {
            LOGGER.error("★ 数据对账完成：{}/{} 条不变量不通过，请人工核查（对账只报告、不自动修复）",
                    failed, results.size());
        }
    }
}
