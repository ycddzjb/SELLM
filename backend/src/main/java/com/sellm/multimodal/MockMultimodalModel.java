package com.sellm.multimodal;

import com.sellm.scale.ScaleItem;
import java.util.ArrayList;
import java.util.List;

/**
 * 默认多模态模型:对每个指标给确定性中位建议分(maxScore/2),不外联。
 * 用于 dev/test 与未配置真实 vision 的环境,保证零出网、结果可预期。
 */
public class MockMultimodalModel implements MultimodalModel {

    @Override
    public List<ItemSuggestion> analyze(byte[] media, String noteText, List<ScaleItem> items) {
        List<ItemSuggestion> out = new ArrayList<>();
        if (items == null) {
            return out;
        }
        for (ScaleItem item : items) {
            double suggested = Math.round(item.getMaxScore() / 2.0);
            out.add(new ItemSuggestion(item.getItemId(), suggested,
                "[Mock建议] 需教师据实际表现确认"));
        }
        return out;
    }
}
