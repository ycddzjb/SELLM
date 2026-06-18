package com.sellm.iep;

public class Iep {
    private final String childName;
    private final String draft;       // AI 生成草案(已还原)
    private String finalizedContent;  // 老师定稿内容
    private IepStatus status;

    public Iep(String childName, String draft) {
        this.childName = childName;
        this.draft = draft;
        this.status = IepStatus.DRAFT;
    }

    public void finalizePlan(String content) {
        this.finalizedContent = content;
        this.status = IepStatus.FINALIZED;
    }

    public String getChildName() { return childName; }
    public String getDraft() { return draft; }
    public String getFinalizedContent() { return finalizedContent; }
    public IepStatus getStatus() { return status; }
}
