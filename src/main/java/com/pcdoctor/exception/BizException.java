package com.pcdoctor.exception;

import com.pcdoctor.common.ErrorCode;
import lombok.Getter;

/**
 * 业务异常：service 层抛出，由 GlobalExceptionHandler 统一转成 Result。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 携带自定义提示信息，覆盖 errorCode 的默认 message */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
