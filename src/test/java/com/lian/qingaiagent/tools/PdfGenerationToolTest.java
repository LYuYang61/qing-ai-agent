package com.lian.qingaiagent.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfGenerationToolTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesPdfWithChineseContent() throws Exception {
        PdfGenerationTool tool = new PdfGenerationTool(new ToolFileStorage(tempDir, 1024 * 1024));

        String result = tool.generatePdf("约会计划.pdf", "周末一起散步，认真沟通。");

        Path pdf = tempDir.resolve("pdf/约会计划.pdf");
        assertTrue(result.startsWith("PDF 生成成功："));
        assertTrue(Files.size(pdf) > 0);
        assertTrue(new String(Files.readAllBytes(pdf), java.nio.charset.StandardCharsets.ISO_8859_1)
                .startsWith("%PDF-"));
    }
}
