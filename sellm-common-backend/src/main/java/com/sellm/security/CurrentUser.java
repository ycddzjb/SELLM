package com.sellm.security;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    /** 取当前登录主体;未认证抛 ACCESS_DENIED。 */
    public AuthPrincipal require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal p)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "未认证");
        }
        return p;
    }
}
