package com.sellm.user;

import com.sellm.security.Role;

public class AppUser {
    private Long id;
    private String username;
    private String passwordHash;
    private Role role;
    private Long orgId;

    public AppUser() {
    }

    public AppUser(Long id, String username, String passwordHash, Role role, Long orgId) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.orgId = orgId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
}
