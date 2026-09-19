package com.mymall.common.exception;

import com.mymall.common.api.IErrorCode;
import com.mymall.common.api.ResultCode;

/** 业务异常：由全局异常处理器统一转成 CommonResult */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = ResultCode.FAILED.getCode();
    }

    public BizException(IErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(IErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public int getCode() { return code; }
}