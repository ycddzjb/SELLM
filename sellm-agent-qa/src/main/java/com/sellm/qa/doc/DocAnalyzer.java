package com.sellm.qa.doc;

/**
 * 文档/图片分析适配器(可切换,默认 mock 不外联)。
 * 红线:只接收已脱敏文本提示 + 文件字节;真实实现出网即配置方自担合规。
 */
public interface DocAnalyzer {
    /**
     * @param filename 原始文件名(判类型)
     * @param mimeType 文件 MIME
     * @param bytes    文件字节
     * @param anonymizedPrompt 已脱敏的用户提问(可空)
     * @return 分析结果文本
     */
    String analyze(String filename, String mimeType, byte[] bytes, String anonymizedPrompt);
}
