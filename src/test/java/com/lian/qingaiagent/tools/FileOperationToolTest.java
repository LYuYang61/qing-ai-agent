package com.lian.qingaiagent.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileOperationToolTest {

    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsUtf8TextInsideWorkspace() {
        FileOperationTool tool = new FileOperationTool(new ToolFileStorage(tempDir, 1024));

        String writeResult = tool.writeFile("恋爱档案.txt", "上海周末约会计划：外滩散步");

        assertTrue(writeResult.startsWith("文件写入成功："));
        assertEquals("上海周末约会计划：外滩散步", tool.readFile("恋爱档案.txt"));
    }

    @Test
    void rejectsPathTraversal() {
        FileOperationTool tool = new FileOperationTool(new ToolFileStorage(tempDir, 1024));

        String result = tool.writeFile("../outside.txt", "不能越界");

        assertTrue(result.startsWith("文件写入失败："));
    }
}
