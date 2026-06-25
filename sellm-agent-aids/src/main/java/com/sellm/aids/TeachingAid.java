package com.sellm.aids;

import java.util.List;

/** 教具库实体(推荐用)。disorderTypes 以 JSON 字符串数组存库,领域对象持 List。 */
public class TeachingAid {
    private Long id;
    private String name;
    private List<String> disorderTypes;
    private String category;
    private String usageGuide;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getDisorderTypes() { return disorderTypes; }
    public void setDisorderTypes(List<String> disorderTypes) { this.disorderTypes = disorderTypes; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getUsageGuide() { return usageGuide; }
    public void setUsageGuide(String usageGuide) { this.usageGuide = usageGuide; }
}
