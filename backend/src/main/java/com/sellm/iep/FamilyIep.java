package com.sellm.iep;

/** 家长家庭 IEP 记录。 */
public class FamilyIep {
    private Long id;
    private Long childId;
    private Long parentUserId;
    private String parentGoal;
    private String draft;
    private String finalizedContent;
    private String status;

    public FamilyIep() {
    }

    public FamilyIep(Long id, Long childId, Long parentUserId, String parentGoal,
                     String draft, String finalizedContent, String status) {
        this.id = id;
        this.childId = childId;
        this.parentUserId = parentUserId;
        this.parentGoal = parentGoal;
        this.draft = draft;
        this.finalizedContent = finalizedContent;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public Long getParentUserId() { return parentUserId; }
    public void setParentUserId(Long parentUserId) { this.parentUserId = parentUserId; }
    public String getParentGoal() { return parentGoal; }
    public void setParentGoal(String parentGoal) { this.parentGoal = parentGoal; }
    public String getDraft() { return draft; }
    public void setDraft(String draft) { this.draft = draft; }
    public String getFinalizedContent() { return finalizedContent; }
    public void setFinalizedContent(String finalizedContent) { this.finalizedContent = finalizedContent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
