package com.mymall.common.exception;

import com.mymall.common.api.IErrorCode;

/**
 * 断言处理类，用于抛出各种API异常
 */
public class Asserts {
    public static void fail(String message) {
        throw new BizException(message);
    }

    public static void fail(IErrorCode errorCode) {
        throw new BizException(errorCode);
    }
}
