package com.sellm.scale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/scale-seed.sql", config = @SqlConfig(encoding = "UTF-8"))
class ScaleRepositoryTest {

    @Autowired
    private ScaleRepository repository;

    @Test
    void 按scaleId组装出完整量表() {
        Scale cars = repository.findById("cars");
        assertThat(cars).isNotNull();
        assertThat(cars.getName()).isEqualTo("CARS");
        assertThat(cars.getItems()).hasSize(2);
        assertThat(cars.getScoringRule()).isNotNull();
        assertThat(cars.getScoringRule().getBands()).hasSize(2);
    }

    @Test
    void 组装的量表可直接用于计分引擎() {
        Scale cars = repository.findById("cars");
        AssessmentResult r = new DefaultScoringEngine().score(cars,
            List.of(new Answer("q1", 2), new Answer("q2", 3)));
        assertThat(r.getBandLabel()).isEqualTo("轻-中度");
    }

    @Test
    void 不存在的量表返回null() {
        assertThat(repository.findById("nope")).isNull();
    }
}
