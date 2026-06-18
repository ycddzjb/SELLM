package com.sellm.anonymizer;

import java.util.List;

public interface Anonymizer {
    /**
     * 对 text 脱敏。names/schools 为已知需替换的身份信息;另外内置身份证号正则。
     * @throws AnonymizationException 当脱敏后文本仍可能残留身份信息(校验未通过)时
     */
    AnonymizationResult anonymize(String text, List<String> names, List<String> schools);

    /** 用还原映射把占位符替换回原值 */
    String restore(String text, java.util.Map<String, String> restoreMap);
}
