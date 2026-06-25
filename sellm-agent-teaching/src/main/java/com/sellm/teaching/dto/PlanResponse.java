package com.sellm.teaching.dto;

public class PlanResponse {
    private Long id;
    private String status;
    private String content;
    private String aiDraft;

    public PlanResponse(Long id, String status, String content, String aiDraft) {
        this.id = id; this.status = status; this.content = content; this.aiDraft = aiDraft;
    }
    public Long getId() { return id; }
    public String getStatus() { return status; }
    public String getContent() { return content; }
    public String getAiDraft() { return aiDraft; }
}
