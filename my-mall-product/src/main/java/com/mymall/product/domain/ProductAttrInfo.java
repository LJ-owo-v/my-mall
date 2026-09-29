package com.mymall.product.domain;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品分类对应属性信息
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ProductAttrInfo {
    @Schema(title = "商品属性ID")
    private Long attributeId;
    @Schema(title = "商品属性分类ID")
    private Long attributeCategoryId;
}
