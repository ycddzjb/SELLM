package com.sellm.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 业务异常转400及对应错误码() {
        var resp = handler.handleBusiness(new BusinessException(ErrorCode.INVALID_INPUT));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getCode()).isEqualTo(ErrorCode.INVALID_INPUT.getCode());
    }

    @Test
    void 行级权限拒绝转403() {
        var resp = handler.handleBusiness(new BusinessException(ErrorCode.ACCESS_DENIED));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().getCode()).isEqualTo(ErrorCode.ACCESS_DENIED.getCode());
    }

    @Test
    void 非法参数转400输入校验() {
        var resp = handler.handleIllegalArgument(new IllegalArgumentException("bad"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getCode()).isEqualTo(ErrorCode.INVALID_INPUT.getCode());
    }
}
