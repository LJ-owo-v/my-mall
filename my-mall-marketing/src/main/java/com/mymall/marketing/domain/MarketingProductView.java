package com.mymall.marketing.domain;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品只读视图（迁移期跨域只读 pms_product 用；不 import 商品域的 model）
 * ★ 必须有 getter/setter：MyBatis 靠字段反射也能赋值，但 Jackson 序列化必须靠 getter，
 *   缺少时会抛 InvalidDefinitionException: No serializer found（实测踩到）
 */
@Data
public class MarketingProductView {
    private Long id;
    private String name;
    private String productSn;
    private BigDecimal price;
    private Integer stock;
}