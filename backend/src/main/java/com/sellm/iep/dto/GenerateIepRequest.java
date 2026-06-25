package com.sellm.iep.dto;

public class GenerateIepRequest {
    private Long reportId;       // 旧链路:基于评估报告
    private Long diagnosisId;    // 新链路:基于多模态诊断结果(优先);与 reportId 二选一
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Long getDiagnosisId() { return diagnosisId; }
    public void setDiagnosisId(Long diagnosisId) { this.diagnosisId = diagnosisId; }
}
