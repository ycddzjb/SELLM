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
        return ResponseEntity.status(status).body(Result.error(e.getErrorCode()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Result.error(ErrorCode.INVALID_INPUT));
    }

    @ExceptionHandler(com.sellm.scale.ScoringException.class)
    public ResponseEntity<Result<Void>> handleScoring(com.sellm.scale.ScoringException e) {
        return ResponseEntity.badRequest().body(Result.error(ErrorCode.INVALID_INPUT));
    }
}
