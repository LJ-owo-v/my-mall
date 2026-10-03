package com.mymall.order.dao;

import com.mymall.order.model.OmsOrderReturnApply;
import com.mymall.order.domain.OmsOrderReturnApplyResult;
import com.mymall.order.domain.OmsReturnApplyQueryParam;

import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface OmsOrderReturnApplyDao {
    List<OmsOrderReturnApply> getList(@Param("queryParam") OmsReturnApplyQueryParam queryParam);

    OmsOrderReturnApplyResult getDetail(Long id);
}
