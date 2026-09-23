package com.musemvp.coreagent.support;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 统一异常处理，见详细设计 §2.7。
 *
 * <p>已知业务异常按 {@link ErrorCode} 映射；未预期异常记录 traceId 后统一返回 {@code M01-E006}
 * （503），前端据此保留页面骨架并提示失败，不白屏（DP-M01-06）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiError> handleBizException(BizException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        String traceId = newTraceId();
        log.warn("业务异常 code={} traceId={} message={}", errorCode.getCode(), traceId, ex.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiError.of(errorCode.getCode(), ex.getMessage(), traceId));
    }

    /**
     * 请求体不可解析或必填参数缺失／类型不符。错误码表未为参数绑定失败单列码位，按 M01-E001（400）返回，
     * 消息替换为通用文案，避免与项目名称校验的语义混淆。
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception ex) {
        String traceId = newTraceId();
        log.warn("请求不合法 traceId={} message={}", traceId, ex.getMessage());
        return ResponseEntity.status(ErrorCode.M01_E001.getHttpStatus())
                .body(ApiError.of(ErrorCode.M01_E001.getCode(), "请求参数不合法", traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(Exception ex) {
        String traceId = newTraceId();
        log.error("系统异常 traceId={}", traceId, ex);
        return ResponseEntity.status(ErrorCode.M01_E006.getHttpStatus())
                .body(ApiError.of(ErrorCode.M01_E006, traceId));
    }

    private String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
