package com.sellm.research.dto;

import java.util.List;

public class GenerateProposalRequest {
    private String topic;
    /** 需脱敏屏蔽的命名 PII;由调用方传入,正则脱敏对中文姓名无能为力。 */
    private List<String> subjectNames;
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public List<String> getSubjectNames() { return subjectNames; }
    public void setSubjectNames(List<String> subjectNames) { this.subjectNames = subjectNames; }
}
