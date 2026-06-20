package com.sellm.multimodal;

import com.sellm.scale.ScaleItem;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class MockMultimodalModelTest {

    private final MockMultimodalModel model = new MockMultimodalModel();

    @Test
    void 每个指标都有建议且分在区间内() {
        List<ScaleItem> items = List.of(
            new ScaleItem("q1", "社交", "社交", 1, 4),
            new ScaleItem("q2", "沟通", "沟通", 2, 6));
        List<ItemSuggestion> out = model.analyze(null, "课堂笔记", items);

        assertThat(out).hasSize(2);
        assertThat(out).extracting(ItemSuggestion::getItemId).containsExactly("q1", "q2");
        for (ItemSuggestion s : out) {
            assertThat(s.getSuggestedScore()).isBetween(0.0, 6.0);
            assertThat(s.getReason()).contains("Mock");
        }
        // maxScore=4 → 中位 2;maxScore=6 → 中位 3
        assertThat(out.get(0).getSuggestedScore()).isEqualTo(2.0);
        assertThat(out.get(1).getSuggestedScore()).isEqualTo(3.0);
    }

    @Test
    void 空items返回空() {
        assertThat(model.analyze(null, null, List.of())).isEmpty();
    }
}
