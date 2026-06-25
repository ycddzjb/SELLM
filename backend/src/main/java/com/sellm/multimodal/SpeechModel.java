package com.sellm.multimodal;

/**
 * 语音识别(ASR)模型:音频字节 → 转写文本。
 * 实现按 sellm.speech.provider 装配(默认 Mock,不外联)。
 * ⚠️ 真实 ASR 会把含儿童语音的音频发往第三方;启用即代表已获监护人知情同意并自担合规风险。
 */
public interface SpeechModel {
    /**
     * @param audio    音频字节
     * @param hint     可选提示(如场景/领域),可为 null
     * @return 转写文本
     */
    String transcribe(byte[] audio, String hint);
}
