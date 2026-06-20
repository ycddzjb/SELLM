package com.sellm.iep.dto;

public class GenerateFamilyIepRequest {
    private Long childId;
    private String parentGoal;

    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public String getParentGoal() { return parentGoal; }
    public void setParentGoal(String parentGoal) { this.parentGoal = parentGoal; }
}
