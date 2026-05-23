package com.hmdp.config;

import com.hmdp.constant.ErrorCode;
import com.hmdp.dto.Result;
import com.hmdp.exception.BusinessException;
import com.hmdp.exception.AiServiceException;
import com.hmdp.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result handleBusinessException(BusinessException e) {
        log.warn("Business error: code={}, detail={}", e.getErrorCode().getCode(), e.getDetail());
        return Result.fail(e.getErrorCode().getCode(), e.getMessage(), e.getDetail());
    }

    @ExceptionHandler(AiServiceException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result handleAiServiceException(AiServiceException e) {
        log.error("AI service error: {}", e.getMessage());
        if (e.isCircuitOpen()) {
            return Result.fail(ErrorCode.AI_CIRCUIT_OPEN.getCode(),
                    ErrorCode.AI_CIRCUIT_OPEN.getDefaultMsg(), null);
        }
        return Result.fail(ErrorCode.AI_TIMEOUT.getCode(), e.getMessage(), null);
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleValidationException(ValidationException e) {
        log.warn("Validation error: {}", e.getFieldErrors());
        return Result.fail(ErrorCode.VALIDATION_ERROR.getCode(), e.getMessage(),
                e.getFieldErrors().toString());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleSpringValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe ->
                errors.put(fe.getField(), fe.getDefaultMessage()));
        return Result.fail(ErrorCode.VALIDATION_ERROR.getCode(), "参数校验失败", errors.toString());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleUnknownException(Exception e) {
        log.error("Unhandled exception", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR.getCode(),
                ErrorCode.INTERNAL_ERROR.getDefaultMsg(), null);
    }
}
