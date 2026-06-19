package com.sellm.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DevSeederSmokeTest {

    @Autowired
    private ApplicationContext ctx;

    @Test
    void test_profile_下DevSeeder不装配() {
        // @Profile("dev") → test profile 下不应存在该 bean
        assertThat(ctx.containsBean("devSeeder")).isFalse();
    }
}
