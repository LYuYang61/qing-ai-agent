package com.lian.qingaiagent.tools;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** PDF 生成工具：使用 iText 的内置中文字体生成学习用报告。 */
public class PdfGenerationTool {

    private static final String NAMESPACE = "pdf";

    private final ToolFileStorage storage;

    public PdfGenerationTool(ToolFileStorage storage) {
        this.storage = storage;
    }

    @Tool(name = "generatePdf", description = "把给定文本生成 PDF 文件并保存到工具 workspace 的 pdf 目录")
    public String generatePdf(
            @ToolParam(description = "生成的 PDF 文件名") String fileName,
            @ToolParam(description = "PDF 中要包含的文本内容") String content) {
        Path target = null;
        try {
            if (content == null || content.getBytes(StandardCharsets.UTF_8).length > storage.getMaxFileBytes()) {
                return "PDF 生成失败：内容超过大小限制";
            }
            target = storage.resolveForWrite(NAMESPACE, fileName);
            try (PdfWriter writer = new PdfWriter(target.toString());
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                // font-asian 提供 STSongStd-Light，避免项目必须携带某个操作系统字体文件。
                PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                document.setFont(font);
                document.add(new Paragraph(content));
            }
            return "PDF 生成成功：" + storage.displayPath(target);
        }
        catch (IOException | RuntimeException exception) {
            deleteQuietly(target);
            return "PDF 生成失败：" + exception.getMessage();
        }
    }

    private void deleteQuietly(Path target) {
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        }
        catch (IOException ignored) {
            // 保留原始生成错误，避免清理异常掩盖真正原因。
        }
    }
}
