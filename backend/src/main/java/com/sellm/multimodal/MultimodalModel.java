package com.sellm.multimodal;

import com.sellm.scale.ScaleItem;
import java.util.List;

/**
 * 多模态评估模型:据图片/笔记给出各量表指标的评分建议。
 * 实现按 sellm.multimodal.provider 装配(默认 Mock,不外联)。
 */
public interface MultimodalModel {
    /**
     * @param media    媒体字节(图片);纯笔记可为 null
     * @param noteText 训练笔记文本;可为 null
     * @param items    目标量表的题目(itemId/dimension/maxScore)
     * @return 每个 item 的评分建议
     */
    List<ItemSuggestion> analyze(byte[] media, String noteText, List<ScaleItem> items);
}
