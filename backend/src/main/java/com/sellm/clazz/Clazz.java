package com.sellm.clazz;

public class Clazz {
    private Long id;
    private String name;
    private Long orgId;
    private String disorderTypes;   // 逗号分隔串(如 "ASD,LANGUAGE")

    public Clazz() {
    }

    public Clazz(Long id, String name, Long orgId, String disorderTypes) {
        this.id = id;
        this.name = name;
        this.orgId = orgId;
        this.disorderTypes = disorderTypes;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public String getDisorderTypes() { return disorderTypes; }
    public void setDisorderTypes(String disorderTypes) { this.disorderTypes = disorderTypes; }
}
