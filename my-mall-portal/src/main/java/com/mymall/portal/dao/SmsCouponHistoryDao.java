package com.mymall.portal.dao;

import com.mymall.portal.domain.SmsCouponHistoryDetail;
import com.mymall.portal.model.SmsCoupon;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SmsCouponHistoryDao {
    List<SmsCouponHistoryDetail> getDetailList(@Param("memberId") Long memberId,
                                               @Param("useStatus") Integer useStatus);

    List<SmsCoupon> getCouponList(@Param("memberId") Long memberId, @Param("useStatus") Integer useStatus);
}
