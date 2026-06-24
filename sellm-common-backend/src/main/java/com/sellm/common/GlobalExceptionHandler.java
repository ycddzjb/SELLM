package com.sellm.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        HttpStatus status = e.getErrorCode() == ErrorCode.ACCESS_DENIED
            ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
        // 用异常实际 message(含 detail,如「输入校验失败: 请等待评估报告定稿后再生成家庭 IEP」),
        // 而非仅 ErrorCode 默认文案,便于前端向用户展示具体原因。
        return ResponseEntity.status(status).body(Result.error(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Result.error(ErrorCode.INVALID_INPUT));
    }
}
