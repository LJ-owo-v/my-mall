package com.mymall.order.service;

import com.mymall.order.model.OmsOrderReturnApply;
import com.mymall.order.domain.OmsOrderReturnApplyResult;
import com.mymall.order.domain.OmsReturnApplyQueryParam;
import com.mymall.order.domain.OmsUpdateStatusParam;

import java.util.List;

public interface OmsOrderReturnApplyService {
    List<OmsOrderReturnApply> list(OmsReturnApplyQueryParam queryParam, Integer pageSize, Integer pageNum);

    int delete(List<Long> ids);

    OmsOrderReturnApplyResult getItem(Long id);

    int updateStatus(Long id, OmsUpdateStatusParam statusParam);
}
