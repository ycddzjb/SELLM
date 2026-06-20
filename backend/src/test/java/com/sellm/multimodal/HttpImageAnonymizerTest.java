package com.sellm.multimodal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HttpImageAnonymizerTest {

    private ImageAnonProperties props() {
        ImageAnonProperties p = new ImageAnonProperties();
        p.setProvider("http");
        p.setEndpoint("https://fake.local/anon");
        p.setApiKey("k");
        return p;
    }

    @Test
    void 空输入原样返回不调服务() {
        HttpImageAnonymizer a = new HttpImageAnonymizer(props()) {
            @Override protected byte[] send(byte[] image) { throw new AssertionError("不该调用"); }
        };
        assertThat(a.sanitize(null)).isNull();
        assertThat(a.sanitize(new byte[0])).isEmpty();
    }

    @Test
    void 走可覆写send返回打码后字节() {
        byte[] masked = {9, 9, 9};
        HttpImageAnonymizer a = new HttpImageAnonymizer(props()) {
            @Override protected byte[] send(byte[] image) { return masked; }
        };
        assertThat(a.sanitize(new byte[]{1, 2, 3})).isEqualTo(masked);
    }

    @Test
    void 服务返回空时抛异常阻断() {
        HttpImageAnonymizer a = new HttpImageAnonymizer(props()) {
            @Override protected byte[] send(byte[] image) { return new byte[0]; }
        };
        assertThatThrownBy(() -> a.sanitize(new byte[]{1}))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void send失败时抛异常阻断不返回原图() {
        HttpImageAnonymizer a = new HttpImageAnonymizer(props()) {
            @Override protected byte[] send(byte[] image) { throw new RuntimeException("脱敏服务返回非 2xx: 500"); }
        };
        assertThatThrownBy(() -> a.sanitize(new byte[]{1, 2, 3}))
            .isInstanceOf(RuntimeException.class);
    }
}
