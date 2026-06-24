package com.sellm.qa.doc;

import java.nio.charset.StandardCharsets;

/** 默认文档分析器:不外联,返回确定性占位分析(dev/test 与未配 key 环境)。 */
public class MockDocAnalyzer implements DocAnalyzer {
    @Override
    public String analyze(String filename, String mimeType, byte[] bytes, String anonymizedPrompt) {
        String kind = (mimeType != null && mimeType.startsWith("image/")) ? "图片"
            : (filename != null && filename.toLowerCase().endsWith(".pdf")) ? "PDF 文档" : "文本文档";
        int size = bytes == null ? 0 : bytes.length;
        StringBuilder sb = new StringBuilder();
        sb.append("[模拟分析] 已接收").append(kind).append("「").append(filename).append("」(")
          .append(size).append(" 字节)。\n");
        if (anonymizedPrompt != null && !anonymizedPrompt.isBlank()) {
            sb.append("结合你的问题:").append(anonymizedPrompt).append("\n");
        }
        // 文本类回显前若干字符,体现"读到了内容"(已脱敏)
        if (mimeType != null && (mimeType.startsWith("text/") || mimeType.contains("json"))) {
            String text = new String(bytes == null ? new byte[0] : bytes, StandardCharsets.UTF_8);
            sb.append("文档摘要(前 200 字):").append(text.length() > 200 ? text.substring(0, 200) : text);
        } else {
            sb.append("(配置 sellm.doc-analyzer.provider=openai + key 后可启用真实多模态分析)");
        }
        return sb.toString();
    }
}
