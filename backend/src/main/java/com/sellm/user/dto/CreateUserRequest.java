package com.sellm.user.dto;

import com.sellm.security.Role;

public class CreateUserRequest {
    private String username;
    private String password;
    private Role role;
    private Long orgId; // 超管创建时指定目标机构;MANAGER 创建时忽略

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
}
