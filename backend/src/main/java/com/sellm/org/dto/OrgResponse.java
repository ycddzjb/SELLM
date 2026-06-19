package com.sellm.org.dto;

public class OrgResponse {
    private Long id;
    private String name;
    private String region;
    private String disorderTypes;
    private String province;
    private String city;

    public OrgResponse() {}

    public OrgResponse(Long id, String name, String region) {
        this(id, name, region, null, null, null);
    }

    public OrgResponse(Long id, String name, String region, String disorderTypes, String province, String city) {
        this.id = id;
        this.name = name;
        this.region = region;
        this.disorderTypes = disorderTypes;
        this.province = province;
        this.city = city;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getDisorderTypes() { return disorderTypes; }
    public void setDisorderTypes(String disorderTypes) { this.disorderTypes = disorderTypes; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
}
