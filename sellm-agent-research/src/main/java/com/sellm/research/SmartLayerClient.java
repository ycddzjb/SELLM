package com.sellm.research;

/** 调 Python 智能层生成课题申报书。 */
public interface SmartLayerClient {
    /** @param topic 已脱敏的课题主题 */
    String generate(String topic);
}
