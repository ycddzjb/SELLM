package com.sellm.multimodal;

import com.sellm.scale.ScaleItem;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class MediaRecognizerTest {

    /** 假 vision:describe 返回固定标记,便于断言路由。 */
    static class FakeVision implements MultimodalModel {
        @Override public List<ItemSuggestion> analyze(byte[] m, String n, List<ScaleItem> i) { return List.of(); }
        @Override public String describe(byte[] media, String noteText) { return "VISION:" + (media == null ? 0 : media.length); }
    }
    /** 假 ASR:转写返回固定标记。 */
    static class FakeSpeech implements SpeechModel {
        @Override public String transcribe(byte[] audio, String hint) { return "ASR:" + (audio == null ? 0 : audio.length); }
    }

    private final MediaRecognizer recognizer = new MediaRecognizer(new FakeVision(), new FakeSpeech());

    @Test
    void TEXT直接返回笔记原文() {
        assertThat(recognizer.recognize("TEXT", null, "剥珠正确率40%")).isEqualTo("剥珠正确率40%");
    }

    @Test
    void TEXT空笔记返回空串() {
        assertThat(recognizer.recognize("TEXT", null, null)).isEmpty();
    }

    @Test
    void IMAGE走vision描述() {
        assertThat(recognizer.recognize("IMAGE", new byte[100], "笔记")).isEqualTo("VISION:100");
    }

    @Test
    void VIDEO首版也走vision描述() {
        assertThat(recognizer.recognize("VIDEO", new byte[200], null)).isEqualTo("VISION:200");
    }

    @Test
    void AUDIO走ASR转写() {
        assertThat(recognizer.recognize("AUDIO", new byte[50], null)).isEqualTo("ASR:50");
    }

    @Test
    void 大小写不敏感() {
        assertThat(recognizer.recognize("audio", new byte[8], null)).isEqualTo("ASR:8");
        assertThat(recognizer.recognize("image", new byte[9], null)).isEqualTo("VISION:9");
    }

    @Test
    void 未知类型退化为文本() {
        assertThat(recognizer.recognize("UNKNOWN", null, "兜底文本")).isEqualTo("兜底文本");
        assertThat(recognizer.recognize(null, null, "空类型")).isEqualTo("空类型");
    }
}
