package com.sellm.export;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * 文本 → PDF 字节流(openhtmltopdf)。
 * 中文需注册 CJK 字体:配置 sellm.pdf.font-path 指向一个 TTF(如 Windows simhei.ttf);
 * 字体缺失时仍能渲染(ASCII 正常,中文可能缺字形),保证无字体环境/测试不崩。
 */
@Component
public class PdfExporter {

    private static final Logger log = LoggerFactory.getLogger(PdfExporter.class);

    private final String fontPath;

    public PdfExporter(@Value("${sellm.pdf.font-path:}") String fontPath) {
        this.fontPath = fontPath;
        if (fontPath == null || fontPath.isBlank()) {
            log.warn("未配置 sellm.pdf.font-path:导出 PDF 的中文将缺字形。请设置环境变量 SELLM_PDF_FONT_PATH 指向 CJK 字体(如 simhei.ttf)。");
        } else if (!new File(fontPath).isFile()) {
            log.warn("sellm.pdf.font-path 指向的字体不存在: {} —— 中文 PDF 将缺字形。", fontPath);
        } else {
            log.info("PDF 中文字体已加载: {}", fontPath);
        }
    }

    public byte[] toPdf(String title, String content) {
        String safeTitle = escape(title == null ? "" : title);
        String safeContent = escape(content == null ? "" : content);
        String fontFace = "";
        boolean hasFont = fontPath != null && !fontPath.isBlank() && new File(fontPath).isFile();
        if (hasFont) {
            fontFace = "@font-face { font-family: 'cjk'; src: url('"
                + new File(fontPath).toURI().toString() + "'); }";
        }
        String bodyFont = hasFont ? "font-family: 'cjk', sans-serif;" : "font-family: sans-serif;";
        String html = "<html><head><meta charset='UTF-8'/><style>"
            + fontFace
            + " body { " + bodyFont + " font-size: 13px; line-height: 1.6; padding: 24px; }"
            + " h1 { font-size: 18px; }"
            + " pre { white-space: pre-wrap; word-wrap: break-word; font-family: inherit; }"
            + "</style></head><body>"
            + "<h1>" + safeTitle + "</h1>"
            + "<pre>" + safeContent + "</pre>"
            + "</body></html>";

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            if (hasFont) {
                builder.useFont(new File(fontPath), "cjk");
            }
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF 生成失败", e);
        }
    }

    private String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
