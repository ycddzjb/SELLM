package com.sellm.security;

/**
 * 行级权限判定所需的儿童最小视图。
 * 解耦 AccessGuard 与 backend 的 Child 实体,使 AccessGuard 可下沉到 sellm-common。
 * backend 的 Child 实现此接口。
 */
public interface ChildSubject {
    Long getOrgId();
    Long getGuardianUserId();
}
