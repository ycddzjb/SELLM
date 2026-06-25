package com.sellm.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.File;

import static org.assertj.core.api.Assertions.*;

class PdfExporterTest {

    @Test
    void 生成非空PDF且以PDF魔数开头() {
        PdfExporter exporter = new PdfExporter(""); // 无字体路径,ASCII 内容
        byte[] pdf = exporter.toPdf("Report", "Line1\nLine2 content");
        assertThat(pdf).isNotEmpty();
        // PDF 文件以 %PDF 开头
        String head = new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
        assertThat(head).isEqualTo("%PDF");
    }

    @Test
    void 转义特殊字符不报错() {
        PdfExporter exporter = new PdfExporter("");
        byte[] pdf = exporter.toPdf("A<b>&C", "x < y & z > w");
        assertThat(pdf).isNotEmpty();
    }

    @Test
    void 配置CJK字体时中文内容能渲染出有效PDF() {
        // 有字体环境(如本机 Windows)才断言;无字体则跳过,保证 CI 不依赖字体
        String font = "C:/Windows/Fonts/simhei.ttf";
        Assumptions.assumeTrue(new File(font).isFile(), "无 CJK 字体,跳过中文渲染断言");
        PdfExporter exporter = new PdfExporter(font);
        byte[] pdf = exporter.toPdf("评估报告", "儿童社交沟通能力评估结论:轻-中度。");
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
