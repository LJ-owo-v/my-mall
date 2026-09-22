package com.mymall.common.exception;

import com.mymall.common.api.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理（Servlet 栈，放 common-web，各业务服务靠 scanBasePackages="com.mymall" 扫到）。
 *
 * 与原项目 com.macro.mall.common.exception.GlobalExceptionHandler 逐项对齐：
 *   ① BizException（等价原 ApiException，由 Asserts.fail 抛出）→ code=500 + 原样 message
 *   ② MethodArgumentNotValidException（@Validated @RequestBody 校验失败）→ validateFailed（code=404）
 *   ③ BindException（表单/查询参数绑定失败）→ validateFailed（code=404）
 *
 * 刻意不做的事（做了会破坏与原服务的 diff 一致性）：
 *   ⚠️ 不加 @ExceptionHandler(Exception.class) 兜底：原项目没有它；且 Spring 6.1+ 的
 *      NoResourceFoundException 也会被它吞掉，把 404 变成 HTTP 200 + body code 500。
 *   ⚠️ 不加 @ResponseStatus：原项目返回的是 HTTP 200 + body code=500，基线就是这么记的。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常。
     * Asserts.fail(message)  → BizException.code = 500
     * Asserts.fail(errorCode)→ BizException.code = errorCode.code
     * 原项目写法是 if (e.getErrorCode() != null) failed(errorCode) else failed(message)，此处用 code 字段等价实现。
     */
    @ExceptionHandler(value = BizException.class)
    public CommonResult<Void> handleBizException(BizException e) {
        LOGGER.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return new CommonResult<>(e.getCode(), e.getMessage(), null);
    }

    /**
     * @Validated @RequestBody 参数校验失败。
     * 文案拼法与原项目完全一致：fieldError.getField() + fieldError.getDefaultMessage()（中间不加分隔符）；
     * 取不到 fieldError 时 message 为 null → validateFailed(null)。
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public CommonResult<Void> handleValidException(MethodArgumentNotValidException e) {
        return CommonResult.validateFailed(buildValidMessage(e.getBindingResult()));
    }

    /**
     * 表单 / 查询参数绑定失败（如 ?status=abc）。
     */
    @ExceptionHandler(value = BindException.class)
    public CommonResult<Void> handleValidException(BindException e) {
        return CommonResult.validateFailed(buildValidMessage(e.getBindingResult()));
    }

    private String buildValidMessage(BindingResult bindingResult) {
        String message = null;
        if (bindingResult != null && bindingResult.hasErrors()) {
            FieldError fieldError = bindingResult.getFieldError();
            if (fieldError != null) {
                message = fieldError.getField() + fieldError.getDefaultMessage();
            }
        }
        return message;
    }
}