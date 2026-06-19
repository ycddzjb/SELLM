package com.sellm.clazz.dto;

public class ClazzResponse {
    private Long id;
    private String name;
    private Long orgId;
    private String disorderTypes;

    public ClazzResponse(Long id, String name, Long orgId, String disorderTypes) {
        this.id = id;
        this.name = name;
        this.orgId = orgId;
        this.disorderTypes = disorderTypes;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Long getOrgId() { return orgId; }
    public String getDisorderTypes() { return disorderTypes; }
}
