package com.sellm.common;

public enum Relationship {
    MOTHER_SON("母子"),
    MOTHER_DAUGHTER("母女"),
    FATHER_SON("父子"),
    FATHER_DAUGHTER("父女");

    private final String label;

    Relationship(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 校验关系码合法;空/null 视为合法(可选填)。非法码抛 IllegalArgumentException。 */
    public static void validate(String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        Relationship.valueOf(code.trim()); // 非法码抛 IllegalArgumentException
    }

    /** 取中文标签;null/空/非法码返回原值(出网展示兜底)。 */
    public static String labelOf(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        try {
            return Relationship.valueOf(code.trim()).getLabel();
        } catch (IllegalArgumentException e) {
            return code;
        }
    }
}
