package com.musemvp.coreagent.support;

/**
 * 业务异常，携带 {@link ErrorCode} 由 {@link GlobalExceptionHandler} 统一映射为 {@code ApiError}。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
