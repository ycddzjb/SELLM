package com.sellm.aigateway;

public interface AiModel {
    /** 输入已脱敏的 prompt,返回模型生成文本(可能仍含占位符) */
    String complete(String anonymizedPrompt);
}
