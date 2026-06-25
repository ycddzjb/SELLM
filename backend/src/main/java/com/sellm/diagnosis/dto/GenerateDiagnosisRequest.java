package com.sellm.diagnosis.dto;

import java.util.List;

/** 生成诊断请求:结构化训练表现 + 已上传素材的识别(素材先经 /media 上传识别)。 */
public class GenerateDiagnosisRequest {
    private Long childId;
    private String scaleId;              // 可空:关联量表(维度引导)
    private String structuredInput;      // 结构化训练表现 JSON(剥珠正确率/眼神互动等)
    private List<String> subjectNames;   // 需脱敏的命名 PII(儿童/家长姓名)

    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public String getScaleId() { return scaleId; }
    public void setScaleId(String scaleId) { this.scaleId = scaleId; }
    public String getStructuredInput() { return structuredInput; }
    public void setStructuredInput(String structuredInput) { this.structuredInput = structuredInput; }
    public List<String> getSubjectNames() { return subjectNames; }
    public void setSubjectNames(List<String> subjectNames) { this.subjectNames = subjectNames; }
}
