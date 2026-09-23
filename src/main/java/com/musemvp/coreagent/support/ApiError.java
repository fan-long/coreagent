package com.musemvp.coreagent.support;

import java.time.Instant;

/**
 * 统一错误响应体，见详细设计 §2.7。
 *
 * <pre>
 * { "code": "M01-E002", "message": "项目名称已存在", "timestamp": "...", "traceId": "..." }
 * </pre>
 */
public record ApiError(String code, String message, Instant timestamp, String traceId) {

    public static ApiError of(ErrorCode errorCode, String traceId) {
        return of(errorCode.getCode(), errorCode.getMessage(), traceId);
    }

    public static ApiError of(String code, String message, String traceId) {
        return new ApiError(code, message, Instant.now(), traceId);
    }
}
