package com.sellm.aids.dto;

import java.util.List;

/** 文生素材提交请求。 */
public class GenerateAssetRequest {
    private String type;
    private String prompt;
    /** 需脱敏屏蔽的命名 PII;由调用方传入,正则脱敏对中文姓名无能为力。 */
    private List<String> subjectNames;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public List<String> getSubjectNames() { return subjectNames; }
    public void setSubjectNames(List<String> subjectNames) { this.subjectNames = subjectNames; }
}
