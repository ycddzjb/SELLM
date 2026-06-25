package com.sellm.qa;

/** 问题文本 → 意图。可切换实现(规则 / LLM)。 */
public interface IntentClassifier {
    Intent classify(String question);
}
