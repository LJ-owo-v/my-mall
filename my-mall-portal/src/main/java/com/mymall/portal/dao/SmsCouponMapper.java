package com.mymall.portal.dao;

import com.mymall.portal.model.SmsCoupon;
import com.mymall.portal.model.SmsCouponExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SmsCouponMapper {
    long countByExample(SmsCouponExample example);

    int deleteByExample(SmsCouponExample example);

    int deleteByPrimaryKey(Long id);

    int insert(SmsCoupon row);

    int insertSelective(SmsCoupon row);

    List<SmsCoupon> selectByExample(SmsCouponExample example);

    SmsCoupon selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") SmsCoupon row, @Param("example") SmsCouponExample example);

    int updateByExample(@Param("row") SmsCoupon row, @Param("example") SmsCouponExample example);

    int updateByPrimaryKeySelective(SmsCoupon row);

    int updateByPrimaryKey(SmsCoupon row);

    /**
     * 原子扣减券库存（修复 #7 超发）。
     * 把「检查 count>0」与「扣减」合并为一条原子语句，按影响行数裁决：
     * 并发下只有一个请求能成功扣减，避免"应用层算旧值 + 整行回写"导致的丢失更新。
     *
     * @param couponId 优惠券 id
     * @return 受影响行数：1=扣减成功；0=库存已领完（count<=0）
     */
    int decreaseCount(@Param("couponId") Long couponId);
}