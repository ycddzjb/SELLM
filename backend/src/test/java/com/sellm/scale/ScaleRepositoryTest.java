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
        assertThat(cars.getDisorderType()).isEqualTo("ASD");
        assertThat(cars.getDescription()).isEqualTo("儿童孤独症评定量表");
        assertThat(cars.getItems()).hasSize(2);
        // 题目按 sort_order 排序,且带每题最高分
        assertThat(cars.getItems().get(0).getItemId()).isEqualTo("q1");
        assertThat(cars.getItems().get(0).getSortOrder()).isEqualTo(1);
        assertThat(cars.getItems().get(0).getMaxScore()).isEqualTo(4);
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

    @Test
    void save后可读回完整量表() {
        Scale s = new Scale("si_test", "感统评估", "v1", "SENSORY_INTEGRATION", "感觉统合",
            List.of(new ScaleItem("i1", "前庭", "前庭觉", 1, 5),
                    new ScaleItem("i2", "触觉", "触觉", 2, 5)),
            new ScoringRule(List.of(
                new ScoreBand(0, 4, "正常", "未见异常"),
                new ScoreBand(5, 10, "失调", "建议训练"))));
        repository.save(s);

        Scale got = repository.findById("si_test");
        assertThat(got).isNotNull();
        assertThat(got.getName()).isEqualTo("感统评估");
        assertThat(got.getDisorderType()).isEqualTo("SENSORY_INTEGRATION");
        assertThat(got.getItems()).hasSize(2);
        assertThat(got.getItems().get(0).getMaxScore()).isEqualTo(5);
        assertThat(got.getScoringRule().getBands()).hasSize(2);
    }

    @Test
    void update整体替换题目与分段() {
        repository.save(new Scale("upd_test", "原名", "v1", "ADHD", null,
            List.of(new ScaleItem("a", "题A", null, 1, 4)),
            new ScoringRule(List.of(new ScoreBand(0, 4, "L1", null)))));

        repository.update(new Scale("upd_test", "新名", "v2", "ADHD", "改后",
            List.of(new ScaleItem("a", "题A", null, 1, 4),
                    new ScaleItem("b", "题B", null, 2, 4)),
            new ScoringRule(List.of(new ScoreBand(0, 8, "L1", null)))));

        Scale got = repository.findById("upd_test");
        assertThat(got.getName()).isEqualTo("新名");
        assertThat(got.getVersion()).isEqualTo("v2");
        assertThat(got.getItems()).hasSize(2);
        assertThat(got.getScoringRule().getBands()).hasSize(1);
        assertThat(got.getScoringRule().getBands().get(0).getUpper()).isEqualTo(8);
    }

    @Test
    void deleteById删除量表及其题目分段() {
        repository.save(new Scale("del_test", "待删", "v1", "LANGUAGE", null,
            List.of(new ScaleItem("x", "题", null, 1, 4)),
            new ScoringRule(List.of(new ScoreBand(0, 4, "L", null)))));
        assertThat(repository.findById("del_test")).isNotNull();

        repository.deleteById("del_test");
        assertThat(repository.findById("del_test")).isNull();
    }

    @Test
    void listAll与按品类筛选() {
        repository.save(new Scale("asd_a", "ASD量表A", "v1", "ASD", null, List.of(), null));
        repository.save(new Scale("lang_a", "语言量表A", "v1", "LANGUAGE", null, List.of(), null));

        assertThat(repository.listAll()).extracting(Scale::getScaleId)
            .contains("asd_a", "lang_a", "cars");
        assertThat(repository.listByDisorderType("LANGUAGE")).extracting(Scale::getScaleId)
            .contains("lang_a").doesNotContain("asd_a");
    }

    @Test
    void save非法品类码抛异常() {
        Scale bad = new Scale("bad_test", "坏量表", "v1", "NOPE", null, List.of(), null);
        assertThatThrownBy(() -> repository.save(bad))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
