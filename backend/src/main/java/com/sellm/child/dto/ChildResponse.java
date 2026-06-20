package com.sellm.child.dto;

import com.sellm.child.Child;

public class ChildResponse {
    private final Long id;
    private final String name;
    private final String disorderType;
    private final Long orgId;
    private final Long guardianUserId;
    private final String baselineSummary;
    private final String annualIepSummary;
    private final String monthlyGoal;
    private final String reassessDate;
    private final String iepDueDate;
    private final String interventionProgress;

    public ChildResponse(Long id, String name, String disorderType, Long orgId, Long guardianUserId) {
        this(id, name, disorderType, orgId, guardianUserId, null, null, null, null, null, null);
    }

    public ChildResponse(Long id, String name, String disorderType, Long orgId, Long guardianUserId,
                         String baselineSummary, String annualIepSummary, String monthlyGoal,
                         String reassessDate, String iepDueDate, String interventionProgress) {
        this.id = id;
        this.name = name;
        this.disorderType = disorderType;
        this.orgId = orgId;
        this.guardianUserId = guardianUserId;
        this.baselineSummary = baselineSummary;
        this.annualIepSummary = annualIepSummary;
        this.monthlyGoal = monthlyGoal;
        this.reassessDate = reassessDate;
        this.iepDueDate = iepDueDate;
        this.interventionProgress = interventionProgress;
    }

    public static ChildResponse of(Child c) {
        return new ChildResponse(c.getId(), c.getName(), c.getDisorderType(), c.getOrgId(),
            c.getGuardianUserId(), c.getBaselineSummary(), c.getAnnualIepSummary(),
            c.getMonthlyGoal(), c.getReassessDate(), c.getIepDueDate(), c.getInterventionProgress());
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDisorderType() { return disorderType; }
    public Long getOrgId() { return orgId; }
    public Long getGuardianUserId() { return guardianUserId; }
    public String getBaselineSummary() { return baselineSummary; }
    public String getAnnualIepSummary() { return annualIepSummary; }
    public String getMonthlyGoal() { return monthlyGoal; }
    public String getReassessDate() { return reassessDate; }
    public String getIepDueDate() { return iepDueDate; }
    public String getInterventionProgress() { return interventionProgress; }
}
