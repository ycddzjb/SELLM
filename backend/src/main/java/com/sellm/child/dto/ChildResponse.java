package com.sellm.child.dto;

public class ChildResponse {
    private final Long id;
    private final String name;
    private final String disorderType;
    private final Long orgId;
    private final Long guardianUserId;

    public ChildResponse(Long id, String name, String disorderType, Long orgId, Long guardianUserId) {
        this.id = id;
        this.name = name;
        this.disorderType = disorderType;
        this.orgId = orgId;
        this.guardianUserId = guardianUserId;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDisorderType() { return disorderType; }
    public Long getOrgId() { return orgId; }
    public Long getGuardianUserId() { return guardianUserId; }
}
