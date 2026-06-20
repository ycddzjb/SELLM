package com.sellm.child;

public class Child {
    private Long id;
    private String name;          // 明文姓名(领域层)
    private String disorderType;
    private Long orgId;
    private Long guardianUserId;  // 关联家长账号(行级权限用)
    // 阶段 D 批一:扩展字段(非 PII 概要,明文)
    private String baselineSummary;      // 基线评估概要
    private String annualIepSummary;     // 年度 IEP 方案概要
    private String monthlyGoal;          // 月度干预目标
    private String reassessDate;         // 复评时间(ISO yyyy-MM-dd 字符串)
    private String iepDueDate;           // IEP 到期日(ISO yyyy-MM-dd 字符串)
    private String interventionProgress; // 干预进度

    public Child() {
    }

    public Child(Long id, String name, String disorderType, Long orgId) {
        this(id, name, disorderType, orgId, null);
    }

    public Child(Long id, String name, String disorderType, Long orgId, Long guardianUserId) {
        this.id = id;
        this.name = name;
        this.disorderType = disorderType;
        this.orgId = orgId;
        this.guardianUserId = guardianUserId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
