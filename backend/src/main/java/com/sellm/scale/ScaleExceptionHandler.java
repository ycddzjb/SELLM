package com.sellm.scale;

import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * scale 模块专属异常处理(从 GlobalExceptionHandler 拆出,
 * 使 GlobalExceptionHandler 不依赖 scale.ScoringException,可下沉 sellm-common)。
 */
@RestControllerAdvice
public class ScaleExceptionHandler {

    @ExceptionHandler(ScoringException.class)
    public ResponseEntity<Result<Void>> handleScoring(ScoringException e) {
        return ResponseEntity.badRequest().body(Result.error(ErrorCode.INVALID_INPUT));
    }
}
