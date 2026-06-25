package com.sellm.qa;

import com.sellm.qa.dto.QaAnswer;

/** 调 Python 智能层做 RAG 问答。可切换实现(HTTP / 测试桩)。 */
public interface SmartLayerClient {
    /** @param anonymizedQuestion 已脱敏的问题文本 */
    QaAnswer generate(String anonymizedQuestion, int topK);
}
