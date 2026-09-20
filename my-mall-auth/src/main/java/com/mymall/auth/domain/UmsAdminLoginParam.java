package com.mymall.auth.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class UmsAdminLoginParam {
    @NotEmpty  //校验约束 —— 这个字段不能是 null 也不能是空字符串
    @Schema(title = "用户名", required = true) //给 Knife4j/OpenAPI 文档看的
    private String username;
    @NotEmpty
    @Schema(title = "密码", required = true)
    private String password;
}
