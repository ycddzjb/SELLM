package com.sellm.iep.dto;

public class IepResponse {
    private final Long id;
    private final String draft;
    private final String finalizedContent;
    private final String status;

    public IepResponse(Long id, String draft, String finalizedContent, String status) {
        this.id = id;
        this.draft = draft;
        this.finalizedContent = finalizedContent;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getDraft() { return draft; }
    public String getFinalizedContent() { return finalizedContent; }
    public String getStatus() { return status; }
}
