package com.sellm.export;

import org.junit.jupiter.api.Test;
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
}
