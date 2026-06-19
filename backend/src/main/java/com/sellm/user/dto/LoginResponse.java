package com.sellm.user.dto;

public class LoginResponse {
    private final String token;
    private final String role;
    private final String username;
    private final Long orgId;
    private final String orgName;

    public LoginResponse(String token, String role, String username, Long orgId, String orgName) {
        this.token = token;
        this.role = role;
        this.username = username;
        this.orgId = orgId;
        this.orgName = orgName;
    }

    public String getToken() { return token; }
    public String getRole() { return role; }
    public String getUsername() { return username; }
    public Long getOrgId() { return orgId; }
    public String getOrgName() { return orgName; }
}
