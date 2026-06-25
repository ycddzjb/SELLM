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

    /**
     * 诊断场景:描述媒体(图片/视频帧)中儿童的训练表现,产自然语言描述(不绑量表)。
     * 供 MediaRecognizer 聚合多模态输入。默认 Mock 退化实现,真实 vision 子类覆盖。
     * @param media    媒体字节(图片或视频帧);可为 null
     * @param noteText 教师笔记;可为 null
     * @return 媒体内容的文字描述
     */
    default String describe(byte[] media, String noteText) {
        int kb = media == null ? 0 : Math.max(1, media.length / 1024);
        return "[Mock影像描述] 收到约 " + kb + "KB 影像,未启用真实视觉模型;请教师据画面补充文字描述。";
    }
}
