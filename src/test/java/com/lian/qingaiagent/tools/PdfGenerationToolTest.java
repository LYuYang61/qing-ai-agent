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

        String result = tool.generatePdf("约会计划.pdf", "周末一起散步，认真沟通。", null);

        Path pdf = tempDir.resolve("pdf/约会计划.pdf");
        assertTrue(result.startsWith("PDF 生成成功："));
        assertTrue(Files.size(pdf) > 0);
        assertTrue(new String(Files.readAllBytes(pdf), java.nio.charset.StandardCharsets.ISO_8859_1)
                .startsWith("%PDF-"));
    }

    /**
     * 回归测试（2026-09-13 T2 排错）：emoji 等增补平面字符会让 UniGB-UCS2-H 编码抛
     * "This encoder only accepts BMP codepoints"；工具应剔除后正常生成 PDF 并说明剔除数量。
     */
    @Test
    void generatesPdfByRemovingCharactersOutsideBmp() throws Exception {
        PdfGenerationTool tool = new PdfGenerationTool(new ToolFileStorage(tempDir, 1024 * 1024));

        String result = tool.generatePdf("emoji.pdf", "约会计划 💕 含 emoji 与正文 📍 内容", null);

        assertTrue(result.startsWith("PDF 生成成功："));
        assertTrue(result.contains("已剔除 2 个"));
        Path pdf = tempDir.resolve("pdf/emoji.pdf");
        assertTrue(Files.size(pdf) > 0);
    }

    /**
     * fromFile 路径：模型可先 writeFile 保存长内容，再让 PDF 工具直接读文件，
     * 避免把同样的长文本作为参数重新生成一遍（token 与时间的主要浪费来源之一）。
     */
    @Test
    void generatesPdfFromWorkspaceFileWithoutRepeatedContent() throws Exception {
        ToolFileStorage storage = new ToolFileStorage(tempDir, 1024 * 1024);
        storage.writeUtf8("file", "plan.txt", "约会计划：先散步，再看展，最后晚餐。");
        PdfGenerationTool tool = new PdfGenerationTool(storage);

        String result = tool.generatePdf("from-file.pdf", null, "plan.txt");

        assertTrue(result.startsWith("PDF 生成成功："));
        assertTrue(Files.size(tempDir.resolve("pdf/from-file.pdf")) > 0);
        assertTrue(tool.generatePdf("bad.pdf", null, "missing.txt").startsWith("PDF 生成失败"));
        assertTrue(tool.generatePdf("bad.pdf", null, null).startsWith("PDF 生成失败"));
    }
}
