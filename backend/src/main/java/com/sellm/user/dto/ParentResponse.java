package com.sellm.user.dto;

import com.sellm.security.Role;

/**
 * 本机构家长列表项(管理员只读)。
 * 本阶段(A)展示现有字段;完整字段(姓名/儿童/关系/班级)依赖阶段 C 家长注册改造。
 */
public class ParentResponse {
    private Long id;
    private String username;
    private Role role;
    private Long orgId;
    private String status;

    public ParentResponse(Long id, String username, Role role, Long orgId, String status) {
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
