package com.sellm.aids.dto;

/** 文生素材提交请求。 */
public class GenerateAssetRequest {
    private String type;
    private String prompt;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
}
