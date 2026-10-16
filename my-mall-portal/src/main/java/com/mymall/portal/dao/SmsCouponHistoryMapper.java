package com.mymall.portal.dao;

import com.mymall.portal.model.SmsCouponHistory;
import com.mymall.portal.model.SmsCouponHistoryExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SmsCouponHistoryMapper {
    long countByExample(SmsCouponHistoryExample example);

    int deleteByExample(SmsCouponHistoryExample example);

    int deleteByPrimaryKey(Long id);

    int insert(SmsCouponHistory row);

    int insertSelective(SmsCouponHistory row);

    List<SmsCouponHistory> selectByExample(SmsCouponHistoryExample example);

    SmsCouponHistory selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") SmsCouponHistory row, @Param("example") SmsCouponHistoryExample example);

    int updateByExample(@Param("row") SmsCouponHistory row, @Param("example") SmsCouponHistoryExample example);

    int updateByPrimaryKeySelective(SmsCouponHistory row);

    int updateByPrimaryKey(SmsCouponHistory row);
    /**
     * 返还优惠券：状态改回未使用，清空使用时间
     */
    int releaseCoupon(@Param("couponId") Long couponId, @Param("memberId") Long memberId);

    /**
     * 条件插入领取记录（修复 #8 限领绕过）。
     * 仅当「该会员已领取该券的数量 < per_limit」时才插入 —— 把"检查"与"插入"合并为一条原子语句。
     *
     * 注意：因 InnoDB 默认 REPEATABLE READ 下，并发事务的 COUNT(*) 子查询可能读到同一快照，
     *       本方法在"毫秒级并发窗口"下不能 100% 兜底；其价值在于把竞态窗口从"先查后写"
     *       的整个方法跨度压缩到"单条语句"的瞬间，大幅降低超领概率。
     *       若需要严格兜底，需配合唯一索引（仅 per_limit=1 时）或分布式锁。
     *
     * @return 受影响行数：1=插入成功；0=已领满（达到 per_limit）
     */
    int insertIfUnderLimit(SmsCouponHistory row);

    /**
     * 条件核销优惠券（修复 #13）—— 仅当该券仍为"未使用"时才置为已使用。
     * <p>
     * 替代原来 updateCouponStatus 里的"selectByExample 查 use_status=0 → 整行回写"（TOCTOU），
     * 把「检查」与「更新」合并为一条原子语句。
     *
     * @return 受影响行数：1=核销成功；0=该券已使用/不存在（说明已被核销过）
     */
    int useCouponIfUnused(@Param("couponId") Long couponId, @Param("memberId") Long memberId);
}