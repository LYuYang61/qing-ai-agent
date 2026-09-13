package com.lian.qingaiagent.tools;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

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

    @Tool(name = "generatePdf", description = "把文本生成 PDF 文件并保存到工具 workspace 的 pdf 目录；"
            + "内容优先用 fromFile 指定已由 writeFile 保存的文件，避免把同样的长文本重新生成一遍；"
            + "PDF 中文字体不支持 emoji，表情符号会被自动剔除，无需预先清理内容")
    public String generatePdf(
            @ToolParam(description = "生成的 PDF 文件名") String fileName,
            @ToolParam(required = false, description = "PDF 中要包含的文本内容；提供 fromFile 时可省略") String content,
            @ToolParam(required = false, description = "已由 writeFile 保存到 file 目录的源文件名，例如 dating_plan_content.txt") String fromFile) {
        Path target = null;
        try {
            String effectiveContent = content;
            if (!StringUtils.hasText(effectiveContent) && StringUtils.hasText(fromFile)) {
                try {
                    effectiveContent = storage.readUtf8("file", fromFile);
                }
                catch (Exception exception) {
                    return "PDF 生成失败：读取源文件 " + fromFile + " 失败：" + exception.getMessage();
                }
            }
            if (!StringUtils.hasText(effectiveContent)) {
                return "PDF 生成失败：请提供 content 文本，或通过 fromFile 指定已保存的内容文件";
            }
            if (effectiveContent.getBytes(StandardCharsets.UTF_8).length > storage.getMaxFileBytes()) {
                return "PDF 生成失败：内容超过大小限制";
            }
            // STSongStd-Light 的 UniGB-UCS2-H 编码只接受 BMP 字符；emoji 等增补平面字符会让
            // iText 抛 "This encoder only accepts BMP codepoints"（实测 2026-09-13 T2：模型
            // 首次生成失败后自行去重试才成功，代价是全文重新生成一遍）。生成前剔除，保证任意内容可落盘。
            int[] removed = {0};
            StringBuilder bmpContent = new StringBuilder(effectiveContent.length());
            effectiveContent.codePoints().filter(codePoint -> {
                if (codePoint <= 0xFFFF) {
                    return true;
                }
                removed[0]++;
                return false;
            }).forEach(bmpContent::appendCodePoint);
            target = storage.resolveForWrite(NAMESPACE, fileName);
            try (PdfWriter writer = new PdfWriter(target.toString());
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                // font-asian 提供 STSongStd-Light，避免项目必须携带某个操作系统字体文件。
                PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                document.setFont(font);
                document.add(new Paragraph(bmpContent.toString()));
            }
            return "PDF 生成成功：" + storage.displayPath(target)
                    + (removed[0] > 0 ? "（已剔除 " + removed[0] + " 个 PDF 字体不支持的字符）" : "");
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
