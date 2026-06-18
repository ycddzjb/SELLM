package com.sellm.aigateway;

import java.util.List;

public class PromptRequest {
    private final String prompt;        // 含身份信息的原始 prompt
    private final List<String> names;   // 需脱敏的姓名
    private final List<String> schools; // 需脱敏的学校

    public PromptRequest(String prompt, List<String> names, List<String> schools) {
        this.prompt = prompt;
        this.names = names;
        this.schools = schools;
    }

    public String getPrompt() { return prompt; }
    public List<String> getNames() { return names; }
    public List<String> getSchools() { return schools; }
}
