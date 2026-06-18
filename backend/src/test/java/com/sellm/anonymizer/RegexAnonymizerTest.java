package com.sellm.anonymizer;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class RegexAnonymizerTest {

    private final RegexAnonymizer anonymizer = new RegexAnonymizer();

    @Test
    void 替换姓名和学校为占位符() {
        String text = "小明在阳光小学表现良好";
        AnonymizationResult r = anonymizer.anonymize(text, List.of("小明"), List.of("阳光小学"));
        assertThat(r.getAnonymizedText()).doesNotContain("小明").doesNotContain("阳光小学");
        assertThat(r.getAnonymizedText()).contains("[儿童1]").contains("[学校1]");
    }

    @Test
    void 替换身份证号() {
        String text = "证件号 110101200001011234 已登记";
        AnonymizationResult r = anonymizer.anonymize(text, List.of(), List.of());
        assertThat(r.getAnonymizedText()).doesNotContain("110101200001011234");
        assertThat(r.getAnonymizedText()).contains("[身份证1]");
    }

    @Test
    void 还原占位符回原值() {
        String text = "小明在阳光小学表现良好";
        AnonymizationResult r = anonymizer.anonymize(text, List.of("小明"), List.of("阳光小学"));
        String restored = anonymizer.restore(r.getAnonymizedText(), r.getRestoreMap());
        assertThat(restored).isEqualTo(text);
    }

    @Test
    void 脱敏后仍残留已知姓名则抛异常硬阻断() {
        // 传入空白姓名,使替换不生效,但校验阶段应发现该姓名仍在文本中
        assertThatThrownBy(() ->
            anonymizer.anonymize("张伟同学", List.of(), List.of(), List.of("张伟"))
        ).isInstanceOf(AnonymizationException.class);
    }

    @Test
    void 四参版本将学校名纳入校验名单残留则硬阻断() {
        // schools 替换列表为空使"阳光小学"未被替换,mustNotContain 含它 → 校验阶段硬阻断
        assertThatThrownBy(() ->
            anonymizer.anonymize("就读于阳光小学", List.of(), List.of(),
                List.of("阳光小学"))
        ).isInstanceOf(AnonymizationException.class);
    }

    @Test
    void 三参版本学校被正常替换且不误伤() {
        AnonymizationResult r = anonymizer.anonymize(
            "就读于阳光小学", List.of(), List.of("阳光小学"));
        assertThat(r.getAnonymizedText()).doesNotContain("阳光小学").contains("[学校1]");
        assertThat(anonymizer.restore(r.getAnonymizedText(), r.getRestoreMap()))
            .isEqualTo("就读于阳光小学");
    }

    @Test
    void 三参委托传入姓名与学校均被脱敏且校验通过() {
        AnonymizationResult r = anonymizer.anonymize(
            "小明就读于阳光小学", List.of("小明"), List.of("阳光小学"));
        assertThat(r.getAnonymizedText())
            .doesNotContain("小明").doesNotContain("阳光小学")
            .contains("[儿童1]").contains("[学校1]");
    }
}
