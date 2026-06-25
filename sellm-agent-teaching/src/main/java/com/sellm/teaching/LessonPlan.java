package com.sellm.teaching;

public class LessonPlan {
    private Long id;
    private Long ownerId;
    private Long childId;
    private Long classId;
    private Long sourceIepId;
    private String scene;        // HOME/SCHOOL/ORG
    private String mode;         // ONE_ON_ONE/GROUP
    private String disorderType;
    private String aiDraft;
    private String content;
    private String status;       // DRAFT/FINALIZED

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public Long getSourceIepId() { return sourceIepId; }
    public void setSourceIepId(Long sourceIepId) { this.sourceIepId = sourceIepId; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getDisorderType() { return disorderType; }
    public void setDisorderType(String disorderType) { this.disorderType = disorderType; }
    public String getAiDraft() { return aiDraft; }
    public void setAiDraft(String aiDraft) { this.aiDraft = aiDraft; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
