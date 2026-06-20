package com.sellm.common;

public enum ErrorCode {
    OK("0", "成功"),
    ANONYMIZATION_FAILED("A001", "脱敏失败,已阻断出网"),
    SCORING_INVALID_INPUT("S001", "计分输入校验失败"),
    SCORING_RULE_MISSING("S002", "计分规则缺失"),
    AI_CALL_FAILED("G001", "AI 调用失败"),
    INVALID_INPUT("C001", "输入校验失败"),
    ACCESS_DENIED("C002", "无权访问该资源");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
