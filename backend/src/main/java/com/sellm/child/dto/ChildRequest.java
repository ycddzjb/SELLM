package com.sellm.child.dto;

public class ChildRequest {
    private String name;
    private String disorderType;
    private Long orgId;
    private Long guardianUserId;   // 可选:家长账号 id

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisorderType() { return disorderType; }
    public void setDisorderType(String disorderType) { this.disorderType = disorderType; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public Long getGuardianUserId() { return guardianUserId; }
    public void setGuardianUserId(Long guardianUserId) { this.guardianUserId = guardianUserId; }
}
