package com.sellm.common;

import java.util.ArrayList;
import java.util.List;

public enum DisorderType {
    ASD("孤独症"),
    DEVELOPMENTAL_DELAY("发育迟缓"),
    INTELLECTUAL("智力障碍"),
    LANGUAGE("语言障碍"),
    SENSORY_INTEGRATION("感统失调"),
    CEREBRAL_PALSY("脑瘫"),
    ADHD("注意缺陷多动障碍"),
    HEARING_VISION("听视障");

    private final String label;

    DisorderType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 校验逗号分隔串里每个码都是合法枚举名;非法抛 IllegalArgumentException。空串/ null 视为合法(无类型)。 */
    public static void validateCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String code : csv.split(",")) {
            String c = code.trim();
            if (c.isEmpty()) continue;
            DisorderType.valueOf(c); // 非法码抛 IllegalArgumentException
        }
    }

    /** 逗号串转枚举列表(忽略空白项);非法码抛异常。 */
    public static List<DisorderType> fromCsv(String csv) {
        List<DisorderType> list = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return list;
        }
        for (String code : csv.split(",")) {
            String c = code.trim();
            if (!c.isEmpty()) {
                list.add(DisorderType.valueOf(c));
            }
        }
        return list;
    }
}
