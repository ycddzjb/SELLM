package com.sellm.aigateway;

/** Mock 模型:回显脱敏 prompt。默认 provider,由 AiModelConfig 装配(不再 @Component 自动扫描)。 */
public class MockAiModel implements AiModel {
    @Override
    public String complete(String anonymizedPrompt) {
        return "[AI草稿] " + anonymizedPrompt;
    }
}
