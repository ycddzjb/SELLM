package com.sellm.org.dto;

public class OrgResponse {
    private Long id;
    private String name;
    private String region;

    public OrgResponse() {}

    public OrgResponse(Long id, String name, String region) {
        this.id = id;
        this.name = name;
        this.region = region;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
}
