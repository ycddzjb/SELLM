package com.sellm.security;

import com.sellm.child.Child;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class AccessGuard {

    /** 判定 principal 能否访问该 child;不能则抛 ACCESS_DENIED(→403)。 */
    public void checkChildAccess(AuthPrincipal principal, Child child) {
        if (canAccess(principal, child)) {
            return;
        }
        throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该儿童档案");
    }

    public boolean canAccess(AuthPrincipal principal, Child child) {
        if (child == null) {
            return false;
        }
        switch (principal.getRole()) {
            case SUPER_ADMIN:
                return true;  // 超管跨机构,可访问任意 child
            case MANAGER:
            case TEACHER:
                // 本机构范围(orgId 相等;principal 无 org 时拒绝)
                return principal.getOrgId() != null
                    && principal.getOrgId().equals(child.getOrgId());
            case PARENT:
                // 仅自己监护的孩子
                return principal.getUserId() != null
                    && principal.getUserId().equals(child.getGuardianUserId());
            default:
                return false;
        }
    }
}
