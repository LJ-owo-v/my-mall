package com.mymall.order.domain;

import com.mymall.order.model.OmsCompanyAddress;
import com.mymall.order.model.OmsOrderReturnApply;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 申请信息封装
 */
public class OmsOrderReturnApplyResult extends OmsOrderReturnApply {
    @Getter
    @Setter
    @Schema(title = "公司收货地址")
    private OmsCompanyAddress companyAddress;
}
