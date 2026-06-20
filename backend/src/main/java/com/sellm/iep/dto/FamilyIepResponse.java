package com.sellm.iep.dto;

import com.sellm.iep.FamilyIep;

public class FamilyIepResponse {
    private final Long id;
    private final Long childId;
    private final String parentGoal;
    private final String draft;
    private final String finalizedContent;
    private final String status;

    public FamilyIepResponse(Long id, Long childId, String parentGoal,
                             String draft, String finalizedContent, String status) {
        this.id = id;
        this.childId = childId;
        this.parentGoal = parentGoal;
        this.draft = draft;
        this.finalizedContent = finalizedContent;
        this.status = status;
    }

    public static FamilyIepResponse of(FamilyIep r) {
        return new FamilyIepResponse(r.getId(), r.getChildId(), r.getParentGoal(),
            r.getDraft(), r.getFinalizedContent(), r.getStatus());
    }

    public Long getId() { return id; }
    public Long getChildId() { return childId; }
    public String getParentGoal() { return parentGoal; }
    public String getDraft() { return draft; }
    public String getFinalizedContent() { return finalizedContent; }
    public String getStatus() { return status; }
}
