package com.sellm.multimodal;

import org.springframework.stereotype.Service;

/**
 * 多模态识别分流:按 media_type 把素材识别为统一的文本结果,供诊断聚合。
 * - TEXT:直接用 noteText
 * - IMAGE:vision 描述
 * - VIDEO:首版抽帧简化为"取字节交 vision 描述"(真实关键帧抽取需 FFmpeg,本期待接)
 * - AUDIO:ASR 转写
 * 注:出网脱敏(noteText 等文本 PII)由调用方在出网前完成;本服务只做模型调度。
 */
@Service
public class MediaRecognizer {

    private final MultimodalModel visionModel;
    private final SpeechModel speechModel;

    public MediaRecognizer(MultimodalModel visionModel, SpeechModel speechModel) {
        this.visionModel = visionModel;
        this.speechModel = speechModel;
    }

    /**
     * @param mediaType TEXT/IMAGE/VIDEO/AUDIO(大小写不敏感)
     * @param bytes     媒体字节(TEXT 可为 null)
     * @param noteText  教师笔记/文本输入(已脱敏)
     * @return 识别文本(图片描述/视频帧描述/语音转写/原文本)
     */
    public String recognize(String mediaType, byte[] bytes, String noteText) {
        String type = mediaType == null ? "TEXT" : mediaType.trim().toUpperCase();
        switch (type) {
            case "AUDIO":
                return speechModel.transcribe(bytes, noteText);
            case "IMAGE":
            case "VIDEO":
                // 视频首版:直接把字节交 vision 描述(真实关键帧抽取待接 FFmpeg)
                return visionModel.describe(bytes, noteText);
            case "TEXT":
            default:
                return noteText == null ? "" : noteText;
        }
    }
}
