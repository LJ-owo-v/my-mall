package com.mymall.product.domain;


import com.mymall.product.model.PmsProductCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 */
public class PmsProductCategoryWithChildrenItem extends PmsProductCategory {
    @Getter
    @Setter
    @Schema(title = "子级分类")
    private List<PmsProductCategory> children;
}
