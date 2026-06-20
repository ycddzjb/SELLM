package com.sellm.aigateway;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AiModelConfigTest {

    private final AiModelConfig config = new AiModelConfig();

    @Test
    void 默认provider为mock时装配MockAiModel() {
        AiProperties p = new AiProperties(); // provider 默认 mock
        assertThat(config.aiModel(p)).isInstanceOf(MockAiModel.class);
    }

    @Test
    void provider为openai但无key时回退Mock() {
        AiProperties p = new AiProperties();
        p.setProvider("openai");
        p.setApiKey("");   // 无 key → 不外联,回退 Mock
        assertThat(config.aiModel(p)).isInstanceOf(MockAiModel.class);
    }

    @Test
    void provider为openai且有key时装配真实适配器() {
        AiProperties p = new AiProperties();
        p.setProvider("openai");
        p.setBaseUrl("https://fake.local");
        p.setApiKey("sk-test");
        assertThat(config.aiModel(p)).isInstanceOf(OpenAiCompatibleModel.class);
    }
}
