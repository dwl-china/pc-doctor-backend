package com.pcdoctor.exception;

import com.pcdoctor.common.ErrorCode;
import com.pcdoctor.common.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：所有异常统一转成 Result。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：UNAUTHORIZED→HTTP 401、FORBIDDEN→HTTP 403，其余 HTTP 200 + 业务码（契约 §1） */
    @ExceptionHandler(BizException.class)
    public org.springframework.http.ResponseEntity<Result<Void>> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        int status = switch (e.getErrorCode()) {
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED.value();
            case FORBIDDEN -> HttpStatus.FORBIDDEN.value();
            default -> HttpStatus.OK.value();
        };
        return org.springframework.http.ResponseEntity
                .status(status)
                .body(Result.fail(e.getErrorCode().getCode(), e.getMessage()));
    }

    /** @Valid 参数校验失败，取第一条错误信息 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError error = e.getBindingResult().getFieldError();
        String message = error == null ? ErrorCode.INVALID_PARAM.getMessage() : error.getDefaultMessage();
        return Result.fail(ErrorCode.INVALID_PARAM.getCode(), message);
    }

    /** 路径参数类型错误（如 /api/appointments/abc） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(ErrorCode.INVALID_PARAM);
    }

    /** 请求体无法解析（JSON 格式错误/编码错误），属客户端问题而非系统故障 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(ErrorCode.INVALID_PARAM.getCode(), "请求体格式错误");
    }

    /** 方法参数校验失败（@RequestParam/@PathVariable 上的 @Min/@Max 等，如分页参数越界） */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse(ErrorCode.INVALID_PARAM.getMessage());
        return Result.fail(ErrorCode.INVALID_PARAM.getCode(), message);
    }

    /** MVC 层的权限拒绝 */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.fail(ErrorCode.FORBIDDEN);
    }

    /** 未知路径 */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        return Result.fail(ErrorCode.NOT_FOUND);
    }

    /** 兜底 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleOther(Exception e) {
        log.error("未处理异常", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }
}
