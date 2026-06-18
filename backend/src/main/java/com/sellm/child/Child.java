package com.sellm.child;

public class Child {
    private Long id;
    private String name;          // 明文姓名(领域层)
    private String disorderType;
    private Long orgId;

    public Child() {
    }

    public Child(Long id, String name, String disorderType, Long orgId) {
        this.id = id;
        this.name = name;
        this.disorderType = disorderType;
        this.orgId = orgId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisorderType() { return disorderType; }
    public void setDisorderType(String disorderType) { this.disorderType = disorderType; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
}
