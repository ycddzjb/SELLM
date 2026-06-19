package com.sellm.org.dto;

public class CreateOrgRequest {
    private String name;
    private String region;

    public CreateOrgRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
}
