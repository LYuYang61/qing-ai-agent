package com.lian.qingaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 文件读写工具：只允许访问工具 workspace 下的 {@code file} 子目录。
 */
public class FileOperationTool {

    private static final String NAMESPACE = "file";

    private final ToolFileStorage storage;

    public FileOperationTool(ToolFileStorage storage) {
        this.storage = storage;
    }

    @Tool(name = "readFile", description = "读取工具 workspace 中指定文件的 UTF-8 文本内容")
    public String readFile(@ToolParam(description = "要读取的文件名，不能访问 workspace 之外的路径") String fileName) {
        try {
            return storage.readUtf8(NAMESPACE, fileName);
        }
        catch (Exception exception) {
            return "文件读取失败：" + exception.getMessage();
        }
    }

    @Tool(name = "writeFile", description = "把文本内容保存到工具 workspace 的文件中")
    public String writeFile(
            @ToolParam(description = "要写入的文件名，不能访问 workspace 之外的路径") String fileName,
            @ToolParam(description = "要保存的 UTF-8 文本内容") String content) {
        try {
            storage.writeUtf8(NAMESPACE, fileName, content);
            return "文件写入成功：" + storage.displayPath(storage.resolveForRead(NAMESPACE, fileName));
        }
        catch (Exception exception) {
            return "文件写入失败：" + exception.getMessage();
        }
    }
}
