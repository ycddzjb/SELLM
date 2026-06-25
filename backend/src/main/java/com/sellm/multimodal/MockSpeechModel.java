package com.sellm.multimodal;

/** 默认 ASR(不外联):返回占位转写,提示需教师补充/确认。 */
public class MockSpeechModel implements SpeechModel {
    @Override
    public String transcribe(byte[] audio, String hint) {
        int kb = audio == null ? 0 : Math.max(1, audio.length / 1024);
        return "[Mock语音转写] 收到约 " + kb + "KB 音频,未启用真实 ASR;请教师据录音补充文字描述后再生成诊断。";
    }
}
