package com.mymall.product.validator;


import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 用于校验**状态类字段**的值是否合法，防止前端传非法值
 */


// 实现 ConstraintValidator 接口
// 第一个泛型：关联的注解类型 FlagValidator
// 第二个泛型：被校验字段的类型是 Integer（所以这个校验器只能用在 Integer 字段上）
public class FlagValidatorClass implements ConstraintValidator<FlagValidator, Integer> {

    // 存注解上配置的允许值（如 {"0","1"}）
    private String[] values;

    // 初始化方法：校验框架拿到注解实例后，先调这个方法
    @Override
    public void initialize(FlagValidator flagValidator) {
        // 把注解上写的 value 数组存下来，供 isValid 使用
        this.values = flagValidator.value();
    }

    // 真正的校验逻辑：返回 true=通过，false=失败
    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext constraintValidatorContext) {
        boolean isValid = false;

        if (value == null) {
            // 字段为空时直接放行
            // （非空校验交给 @NotNull，这个注解只管"值是否在允许范围内"）
            return true;
        }

        // 遍历允许值，逐个比较
        for (int i = 0; i < values.length; i++) {
            // 把 Integer 转成 String 再比较（因为 values 是 String 数组）
            if (values[i].equals(String.valueOf(value))) {
                isValid = true;
                break;
            }
        }

        return isValid;
    }
}
