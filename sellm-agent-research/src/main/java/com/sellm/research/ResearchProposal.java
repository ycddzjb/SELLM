package com.sellm.research;

public class ResearchProposal {
    private Long id;
    private Long ownerId;
    private String topic;
    private String aiDraft;
    private String content;
    private String status;   // DRAFT/FINALIZED

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getAiDraft() { return aiDraft; }
    public void setAiDraft(String aiDraft) { this.aiDraft = aiDraft; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
