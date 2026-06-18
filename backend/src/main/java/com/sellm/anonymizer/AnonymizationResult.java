package com.sellm.anonymizer;

import java.util.Map;

public class AnonymizationResult {
    private final String anonymizedText;
    private final Map<String, String> restoreMap; // 占位符 -> 原值

    public AnonymizationResult(String anonymizedText, Map<String, String> restoreMap) {
        this.anonymizedText = anonymizedText;
        this.restoreMap = restoreMap;
    }

    public String getAnonymizedText() { return anonymizedText; }
    public Map<String, String> getRestoreMap() { return restoreMap; }
}
