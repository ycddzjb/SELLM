package com.sellm.training;

/** 训练周期/阶段:串起诊断→IEP→训练数据→阶段评估。一个 child 多个周期,seq 递增。 */
public class TrainingCycle {
    private Long id;
    private Long childId;
    private Long ownerId;
    private Long diagnosisId;
    private Long iepId;
    private int seq;
    private String title;
    private String status;   // ACTIVE / CLOSED

    public TrainingCycle() {}

    public TrainingCycle(Long id, Long childId, Long ownerId, Long diagnosisId, Long iepId,
                         int seq, String title, String status) {
        this.id = id;
        this.childId = childId;
        this.ownerId = ownerId;
        this.diagnosisId = diagnosisId;
        this.iepId = iepId;
        this.seq = seq;
        this.title = title;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getDiagnosisId() { return diagnosisId; }
    public void setDiagnosisId(Long diagnosisId) { this.diagnosisId = diagnosisId; }
    public Long getIepId() { return iepId; }
    public void setIepId(Long iepId) { this.iepId = iepId; }
    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
