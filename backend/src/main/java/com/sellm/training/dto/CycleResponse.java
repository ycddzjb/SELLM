package com.sellm.training.dto;

import com.sellm.training.TrainingCycle;

public class CycleResponse {
    private Long id;
    private Long childId;
    private Long diagnosisId;
    private Long iepId;
    private int seq;
    private String title;
    private String status;

    public static CycleResponse of(TrainingCycle c) {
        CycleResponse r = new CycleResponse();
        r.id = c.getId();
        r.childId = c.getChildId();
        r.diagnosisId = c.getDiagnosisId();
        r.iepId = c.getIepId();
        r.seq = c.getSeq();
        r.title = c.getTitle();
        r.status = c.getStatus();
        return r;
    }

    public Long getId() { return id; }
    public Long getChildId() { return childId; }
    public Long getDiagnosisId() { return diagnosisId; }
    public Long getIepId() { return iepId; }
    public int getSeq() { return seq; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
}
