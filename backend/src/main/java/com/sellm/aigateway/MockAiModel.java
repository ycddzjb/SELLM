package com.sellm.aigateway;

import org.springframework.stereotype.Component;

@Component
public class MockAiModel implements AiModel {
    @Override
    public String complete(String anonymizedPrompt) {
        return "[AI草稿] " + anonymizedPrompt;
    }
}
