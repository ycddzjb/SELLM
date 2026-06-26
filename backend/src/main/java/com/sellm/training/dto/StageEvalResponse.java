package com.sellm.training.dto;

import com.sellm.training.StageEval;

public class StageEvalResponse {
    private Long id;
    private Long cycleId;
    private Long childId;
    private String scoresSummary;
    private String deltaSummary;
    private String draft;
    private String finalizedContent;
    private String status;

    public static StageEvalResponse of(StageEval e) {
        StageEvalResponse r = new StageEvalResponse();
        r.id = e.getId();
        r.cycleId = e.getCycleId();
        r.childId = e.getChildId();
        r.scoresSummary = e.getScoresSummary();
        r.deltaSummary = e.getDeltaSummary();
        r.draft = e.getDraft();
        r.finalizedContent = e.getFinalizedContent();
        r.status = e.getStatus();
        return r;
    }

    public Long getId() { return id; }
    public Long getCycleId() { return cycleId; }
    public Long getChildId() { return childId; }
    public String getScoresSummary() { return scoresSummary; }
    public String getDeltaSummary() { return deltaSummary; }
    public String getDraft() { return draft; }
    public String getFinalizedContent() { return finalizedContent; }
    public String getStatus() { return status; }
}
