package com.sellm.clazz.dto;

public class ClazzRequest {
    private String name;
    private String disorderTypes;   // 逗号分隔串(多选)

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisorderTypes() { return disorderTypes; }
    public void setDisorderTypes(String disorderTypes) { this.disorderTypes = disorderTypes; }
}
