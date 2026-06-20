package com.sellm.aigateway;

public interface AiGateway {
    /** 脱敏 → 调模型 → 还原。业务模块只看得到还原后的结果。 */
    String generate(PromptRequest request);
}
