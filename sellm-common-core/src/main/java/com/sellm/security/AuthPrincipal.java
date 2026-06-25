package com.sellm.security;

public class AuthPrincipal {
    private final Long userId;
    private final String username;
    private final Role role;
    private final Long orgId;

    public AuthPrincipal(Long userId, String username, Role role, Long orgId) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.orgId = orgId;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public Role getRole() { return role; }
    public Long getOrgId() { return orgId; }
}
