package com.sellm.multimodal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ImageAnonConfigTest {

    private final ImageAnonConfig config = new ImageAnonConfig();

    @Test
    void 默认provider为noop装配Noop() {
        assertThat(config.imageAnonymizer(new ImageAnonProperties()))
            .isInstanceOf(NoopImageAnonymizer.class);
    }

    @Test
    void provider为http但endpoint空回退Noop() {
        ImageAnonProperties p = new ImageAnonProperties();
        p.setProvider("http");
        p.setEndpoint("");
        assertThat(config.imageAnonymizer(p)).isInstanceOf(NoopImageAnonymizer.class);
    }

    @Test
    void provider为http且endpoint齐全装配Http() {
        ImageAnonProperties p = new ImageAnonProperties();
        p.setProvider("http");
        p.setEndpoint("http://localhost:9999/anon");
        assertThat(config.imageAnonymizer(p)).isInstanceOf(HttpImageAnonymizer.class);
    }

    @Test
    void Noop原样返回() {
        NoopImageAnonymizer n = new NoopImageAnonymizer();
        byte[] data = {1, 2, 3};
        assertThat(n.sanitize(data)).isSameAs(data);
        assertThat(n.sanitize(null)).isNull();
    }
}
