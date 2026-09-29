package com.mymall.product.validator;


import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented                                    // 生成 JavaDoc 时包含这个注解说明
@Retention(RetentionPolicy.RUNTIME)            // 运行时保留，反射能读取到（校验框架靠反射工作）
@Target({ElementType.FIELD, ElementType.PARAMETER})  // 只能用在字段和方法参数上
@Constraint(validatedBy = FlagValidatorClass.class)  // ★ 指定真正执行校验逻辑的类
public @interface FlagValidator {

    String[] value() default {};               // 允许的值列表，如 @FlagValidator(value={"0","1"})

    String message() default "flag is not found";  // 校验失败时的提示信息

    Class<?>[] groups() default {};            // 校验分组（JSR-303 标准属性，必须有）

    Class<? extends Payload>[] payload() default {}; // 负载信息（JSR-303 标准属性，必须有）
}
