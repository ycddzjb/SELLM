package com.sellm.teaching.dto;

import java.util.List;

public class GeneratePlanRequest {
    private Long childId;
    private Long classId;
    private Long sourceIepId;
    private String scene;
    private String mode;
    private String disorderType;
    private String iepContent;
    /** 需脱敏屏蔽的命名 PII(儿童/家长/校名等);由已认证调用方传入,agent 自身无 Child 表无法获取。
        正则脱敏对中文姓名无能为力,必须靠此显式屏蔽表使「出网前脱敏硬阻断」对姓名生效。 */
    private List<String> subjectNames;

    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public Long getSourceIepId() { return sourceIepId; }
    public void setSourceIepId(Long sourceIepId) { this.sourceIepId = sourceIepId; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getDisorderType() { return disorderType; }
    public void setDisorderType(String disorderType) { this.disorderType = disorderType; }
    public String getIepContent() { return iepContent; }
    public void setIepContent(String iepContent) { this.iepContent = iepContent; }
    public List<String> getSubjectNames() { return subjectNames; }
    public void setSubjectNames(List<String> subjectNames) { this.subjectNames = subjectNames; }
}
