package com.sellm.iep;

public class Iep {
    private final String childName;
    private final String draft;   // AI 生成草案(已还原)
    private IepStatus status;

    public Iep(String childName, String draft) {
        this.childName = childName;
        this.draft = draft;
        this.status = IepStatus.DRAFT;
    }

    public void finalizePlan() {
        this.status = IepStatus.FINALIZED;
    }

    public String getChildName() { return childName; }
    public String getDraft() { return draft; }
    public IepStatus getStatus() { return status; }
}
