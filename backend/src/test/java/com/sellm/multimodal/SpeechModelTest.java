package com.sellm.multimodal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SpeechModelTest {

    private final MultimodalConfig config = new MultimodalConfig();

    @Test
    void Mock转写返回占位且不为空() {
        MockSpeechModel m = new MockSpeechModel();
        String t = m.transcribe(new byte[2048], "社交场景");
        assertThat(t).contains("Mock");
        assertThat(t).isNotBlank();
    }

    @Test
    void Mock转写空音频不报错() {
        assertThat(new MockSpeechModel().transcribe(null, null)).isNotBlank();
    }

    @Test
    void 默认provider装配MockSpeech() {
        assertThat(config.speechModel(new SpeechProperties()))
            .isInstanceOf(MockSpeechModel.class);
    }

    @Test
    void provider为openai但无key回退Mock() {
        SpeechProperties p = new SpeechProperties();
        p.setProvider("openai");
        p.setApiKey("");
        assertThat(config.speechModel(p)).isInstanceOf(MockSpeechModel.class);
    }

    @Test
    void provider为openai且有key装配真实ASR() {
        SpeechProperties p = new SpeechProperties();
        p.setProvider("openai");
        p.setBaseUrl("https://fake.local");
        p.setApiKey("sk-test");
        assertThat(config.speechModel(p)).isInstanceOf(OpenAiSpeechModel.class);
    }
}
