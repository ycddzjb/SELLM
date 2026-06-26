package com.sellm.training;

/** 阶段评估报告:量化 delta(本周期 vs 上一周期)+ AI 叙述(提升/未达标/适配性建议)。 */
public class StageEval {
    private Long id;
    private Long cycleId;
    private Long childId;
    private String scoresSummary;   // JSON:本期各指标得分汇总
    private String deltaSummary;    // JSON:对比上一周期 delta/达标判定
    private String draft;           // AI 叙述草案
    private String finalizedContent;
    private String status;          // DRAFT / FINALIZED

    public StageEval() {}

    public StageEval(Long id, Long cycleId, Long childId, String scoresSummary, String deltaSummary,
                     String draft, String finalizedContent, String status) {
        this.id = id;
        this.cycleId = cycleId;
        this.childId = childId;
        this.scoresSummary = scoresSummary;
        this.deltaSummary = deltaSummary;
        this.draft = draft;
        this.finalizedContent = finalizedContent;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCycleId() { return cycleId; }
    public void setCycleId(Long cycleId) { this.cycleId = cycleId; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public String getScoresSummary() { return scoresSummary; }
    public void setScoresSummary(String scoresSummary) { this.scoresSummary = scoresSummary; }
    public String getDeltaSummary() { return deltaSummary; }
    public void setDeltaSummary(String deltaSummary) { this.deltaSummary = deltaSummary; }
    public String getDraft() { return draft; }
    public void setDraft(String draft) { this.draft = draft; }
    public String getFinalizedContent() { return finalizedContent; }
    public void setFinalizedContent(String finalizedContent) { this.finalizedContent = finalizedContent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
