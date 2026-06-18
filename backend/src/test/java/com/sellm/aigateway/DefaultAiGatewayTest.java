package com.sellm.aigateway;

import com.sellm.anonymizer.RegexAnonymizer;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class DefaultAiGatewayTest {

    private final RegexAnonymizer anonymizer = new RegexAnonymizer();

    @Test
    void 调模型前脱敏调用后还原() {
        // Mock 模型回显收到的 prompt,以此验证传给模型的是脱敏文本
        AiModel echo = new MockAiModel();
        DefaultAiGateway gateway = new DefaultAiGateway(anonymizer, echo);

        String result = gateway.generate(new PromptRequest(
            "请为小明在阳光小学的表现生成报告", List.of("小明"), List.of("阳光小学")));

        // 返回结果已还原,含原始身份信息
        assertThat(result).contains("小明").contains("阳光小学");
    }

    @Test
    void 传给底层模型的是脱敏文本() {
        StringBuilder captured = new StringBuilder();
        AiModel capturing = prompt -> { captured.append(prompt); return "ok"; };
        DefaultAiGateway gateway = new DefaultAiGateway(anonymizer, capturing);

        gateway.generate(new PromptRequest(
            "小明在阳光小学", List.of("小明"), List.of("阳光小学")));

        assertThat(captured.toString()).doesNotContain("小明").doesNotContain("阳光小学");
        assertThat(captured.toString()).contains("[儿童1]").contains("[学校1]");
    }

    @Test
    void 模型抛异常时包装为网关异常() {
        AiModel failing = prompt -> { throw new RuntimeException("timeout"); };
        DefaultAiGateway gateway = new DefaultAiGateway(anonymizer, failing);

        assertThatThrownBy(() ->
            gateway.generate(new PromptRequest("文本", List.of(), List.of()))
        ).isInstanceOf(AiGatewayException.class);
    }
}
