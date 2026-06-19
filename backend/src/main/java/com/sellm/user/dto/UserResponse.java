package com.sellm.user.dto;

import com.sellm.security.Role;

/** 用户响应:绝不含 passwordHash。 */
public class UserResponse {
    private Long id;
    private String username;
    private Role role;
    private Long orgId;
    private String status;

    public UserResponse(Long id, String username, Role role, Long orgId, String status) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.orgId = orgId;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public Role getRole() { return role; }
    public Long getOrgId() { return orgId; }
    public String getStatus() { return status; }
}
