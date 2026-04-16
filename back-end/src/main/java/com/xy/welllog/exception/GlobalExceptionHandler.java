package com.xy.welllog.exception;

import com.xy.welllog.common.Result;
import com.xy.welllog.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = BusinessException.class)
    public Result<Object> handleBusinessException(BusinessException e) {
        log.error("业务异常: {}", e.getMessage(), e);
        ResultCode code = e.getResultCode() != null ? e.getResultCode() : ResultCode.FAILED;
        return Result.failed(code, e.getMessage());
    }

    @ExceptionHandler(value = Exception.class)
    public Result<Object> handleException(Exception e) {
        log.error("系统内部异常: {}", e.getMessage(), e);
        return Result.failed("系统内部异常，请联系管理员");
    }
}
