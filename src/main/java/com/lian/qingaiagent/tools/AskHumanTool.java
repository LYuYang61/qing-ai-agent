package com.lian.qingaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 人机协作工具。
 *
 * <p>HTTP 服务不能在工具线程中直接调用 {@code Scanner.nextLine()} 阻塞等待，因此工具返回一个
 * 明确标记，由 ToolCallAgent 转换为 WAITING_FOR_USER，调用方再通过 resume 接口提交答案。</p>
 */
public class AskHumanTool {

    public static final String TOOL_NAME = "askHuman";
    public static final String MARKER = "HUMAN_INPUT_REQUIRED:";

    @Tool(name = TOOL_NAME, description = "当缺少完成任务所需的关键用户信息时暂停任务并向用户提问；不要用它询问可以通过工具获得的信息")
    public String askHuman(@ToolParam(description = "需要用户补充的具体问题") String question) {
        String normalized = question == null || question.isBlank()
                ? "请补充完成任务所需的信息。" : question.trim();
        return MARKER + " " + normalized;
    }

    public static String questionFromMarker(String result) {
        if (result == null) {
            return null;
        }
        String normalized = result.trim();
        // Spring AI 的默认 ToolCallResultConverter 会把 String 结果序列化为 JSON 字符串，
        // 因此实际 responseData 可能是 "HUMAN_INPUT_REQUIRED: ..."，需要去掉外层引号。
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\\\", "\\");
        }
        if (!normalized.startsWith(MARKER)) {
            return null;
        }
        return normalized.substring(MARKER.length()).trim();
    }
}
