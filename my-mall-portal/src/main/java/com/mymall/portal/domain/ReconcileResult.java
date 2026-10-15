package com.mymall.portal.domain;

/**
 * 对账结果（M5 #12）
 *
 * 每条不变量一行：编号 / 名称 / 命中数 / 判定 / 说明
 * 设计为结构化对象（而非拼字符串），将来若要暴露成管理端接口只需在 Controller 包一层。
 */
public class ReconcileResult {

    /** 不变量编号：I1~I5 */
    private String code;

    /** 不变量名称，如"库存守恒" */
    private String name;

    /** 命中（违反）条数：0 = 通过 */
    private Integer hitCount;

    /** 判定：PASS / FAIL */
    private String verdict;

    /** 差异明细摘要（供日志输出，如 "[SKU 201: lock_stock=1 但订单侧=0]"） */
    private String detail;

    public ReconcileResult() {
    }

    public ReconcileResult(String code, String name, Integer hitCount, String detail) {
        this.code = code;
        this.name = name;
        this.hitCount = hitCount;
        this.verdict = (hitCount == null || hitCount == 0) ? "PASS" : "FAIL";
        this.detail = detail;
    }

    /** 是否通过 */
    public boolean isPassed() {
        return hitCount == null || hitCount == 0;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getHitCount() {
        return hitCount;
    }

    public void setHitCount(Integer hitCount) {
        this.hitCount = hitCount;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    @Override
    public String toString() {
        return code + " " + name + " 命中=" + hitCount + " " + verdict
                + (detail == null || detail.isEmpty() ? "" : " 明细=" + detail);
    }
}
