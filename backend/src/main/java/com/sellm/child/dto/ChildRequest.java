package com.sellm.child.dto;

public class ChildRequest {
    private String name;
    private String disorderType;
    private Long orgId;
    private Long guardianUserId;   // 可选:家长账号 id
    // 阶段 D 批一:扩展字段
    private String baselineSummary;
    private String annualIepSummary;
    private String monthlyGoal;
    private String reassessDate;
    private String iepDueDate;
    private String interventionProgress;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisorderType() { return disorderType; }
    public void setDisorderType(String disorderType) { this.disorderType = disorderType; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public Long getGuardianUserId() { return guardianUserId; }
    public void setGuardianUserId(Long guardianUserId) { this.guardianUserId = guardianUserId; }
    public String getBaselineSummary() { return baselineSummary; }
    public void setBaselineSummary(String baselineSummary) { this.baselineSummary = baselineSummary; }
    public String getAnnualIepSummary() { return annualIepSummary; }
    public void setAnnualIepSummary(String annualIepSummary) { this.annualIepSummary = annualIepSummary; }
    public String getMonthlyGoal() { return monthlyGoal; }
    public void setMonthlyGoal(String monthlyGoal) { this.monthlyGoal = monthlyGoal; }
    public String getReassessDate() { return reassessDate; }
    public void setReassessDate(String reassessDate) { this.reassessDate = reassessDate; }
    public String getIepDueDate() { return iepDueDate; }
    public void setIepDueDate(String iepDueDate) { this.iepDueDate = iepDueDate; }
    public String getInterventionProgress() { return interventionProgress; }
    public void setInterventionProgress(String interventionProgress) { this.interventionProgress = interventionProgress; }
}
