package com.mymall.product.domain;

import com.mymall.product.validator.FlagValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 品牌传递参数
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PmsBrandParam {
    @NotEmpty
    @Schema(title = "品牌名称",required = true)
    private String name;
    @Schema(title = "品牌首字母")
    private String firstLetter;
    @Min(value = 0)
    @Schema(title = "排序字段")
    private Integer sort;
    @FlagValidator(value = {"0","1"}, message = "厂家状态不正确")
    @Schema(title = "是否为厂家制造商")
    private Integer factoryStatus;
    @FlagValidator(value = {"0","1"}, message = "显示状态不正确")
    @Schema(title = "是否进行显示")
    private Integer showStatus;
    @NotEmpty
    @Schema(title = "品牌logo",required = true)
    private String logo;
    @Schema(title = "品牌大图")
    private String bigPic;
    @Schema(title = "品牌故事")
    private String brandStory;
}
