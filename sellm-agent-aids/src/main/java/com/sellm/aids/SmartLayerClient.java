package com.sellm.aids;

/** 调 Python 智能层生成文生素材(文本描述 + 可选媒体)。 */
public interface SmartLayerClient {
    /**
     * @param type   素材类型(IMAGE/PICTUREBOOK/VIDEO/AUDIO)
     * @param prompt 已脱敏的生成提示
     * @return 文本描述 + 可选媒体二进制
     */
    GeneratedContent generate(String type, String prompt);
}
