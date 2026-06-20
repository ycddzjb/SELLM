package com.sellm.common;

public enum LogType {
    CLASSROOM_TRACK("课堂追踪"),
    HOME_COMMUNICATION("家校沟通"),
    STAGE_REVIEW("阶段复盘");

    private final String label;

    LogType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 校验记录类型码合法;空/null 抛异常(记录类型必填)。非法码抛 IllegalArgumentException。 */
    public static void validate(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("记录类型不能为空");
        }
        LogType.valueOf(code.trim());
    }

    /** 取中文标签;null/空/非法码返回原值(出网展示兜底)。 */
    public static String labelOf(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        try {
            return LogType.valueOf(code.trim()).getLabel();
        } catch (IllegalArgumentException e) {
            return code;
        }
    }
}
