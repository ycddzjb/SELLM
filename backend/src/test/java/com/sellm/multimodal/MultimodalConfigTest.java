package com.sellm.multimodal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MultimodalConfigTest {

    private final MultimodalConfig config = new MultimodalConfig();
    private final ImageAnonymizer anon = new NoopImageAnonymizer();

    @Test
    void 默认provider为mock装配Mock() {
        assertThat(config.multimodalModel(new MultimodalProperties(), anon))
            .isInstanceOf(MockMultimodalModel.class);
    }

    @Test
    void provider为openai但无key回退Mock() {
        MultimodalProperties p = new MultimodalProperties();
        p.setProvider("openai");
        p.setApiKey("");
        assertThat(config.multimodalModel(p, anon)).isInstanceOf(MockMultimodalModel.class);
    }

    @Test
    void provider为openai且有key装配真实Vision() {
        MultimodalProperties p = new MultimodalProperties();
        p.setProvider("openai");
        p.setBaseUrl("https://fake.local");
        p.setApiKey("sk-test");
        assertThat(config.multimodalModel(p, anon)).isInstanceOf(OpenAiVisionModel.class);
    }
}
