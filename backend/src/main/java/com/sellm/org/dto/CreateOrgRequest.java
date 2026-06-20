package com.sellm.org.dto;

public class CreateOrgRequest {
    private String name;
    private String region;
    private String disorderTypes;
    private String province;
    private String city;
    private String managerUsername;
    private String managerPassword;

    public CreateOrgRequest() {}

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
    public String getManagerUsername() { return managerUsername; }
    public void setManagerUsername(String managerUsername) { this.managerUsername = managerUsername; }
    public String getManagerPassword() { return managerPassword; }
    public void setManagerPassword(String managerPassword) { this.managerPassword = managerPassword; }
}
