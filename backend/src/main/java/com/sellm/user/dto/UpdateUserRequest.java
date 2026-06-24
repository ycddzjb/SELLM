package com.sellm.user.dto;

import com.sellm.security.Role;

/** 超管编辑用户:可改状态/角色/机构(字段为 null 则不改)。 */
public class UpdateUserRequest {
    private String status;   // ACTIVE / PENDING / DISABLED ...
    private Role role;       // 目标角色
    private Long orgId;      // 目标机构

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
}
