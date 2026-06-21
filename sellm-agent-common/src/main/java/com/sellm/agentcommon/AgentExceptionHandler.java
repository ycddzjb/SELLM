package com.sellm.agentcommon;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Agent 业务服务共享异常处理:鉴权/业务异常 → Result 信封 + HTTP 状态。 */
@RestControllerAdvice
public class AgentExceptionHandler {

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Result<Void>> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(ErrorCode.UNAUTHORIZED));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        HttpStatus status = e.getErrorCode() == ErrorCode.ACCESS_DENIED ? HttpStatus.FORBIDDEN
            : e.getErrorCode() == ErrorCode.NOT_FOUND ? HttpStatus.NOT_FOUND
            : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Result.error(e.getErrorCode()));
    }
}
