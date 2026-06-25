package com.sellm.teaching;

public class Courseware {
    private Long id;
    private Long ownerId;
    private Long lessonPlanId;
    private String disorderType;
    private String aiDraft;
    private String content;
    private String storageKey;
    private String format;       // TEXT/HTML
    private String status;       // DRAFT/FINALIZED

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getLessonPlanId() { return lessonPlanId; }
    public void setLessonPlanId(Long lessonPlanId) { this.lessonPlanId = lessonPlanId; }
    public String getDisorderType() { return disorderType; }
    public void setDisorderType(String disorderType) { this.disorderType = disorderType; }
    public String getAiDraft() { return aiDraft; }
    public void setAiDraft(String aiDraft) { this.aiDraft = aiDraft; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
