package com.lian.qingaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** 由模型显式调用以结束当前 Agent Loop 的工具。 */
public class TerminateTool {

    public static final String TOOL_NAME = "terminate";

    @Tool(name = TOOL_NAME, description = "当用户目标已经完成，或确认无法继续时结束任务；不要在还有待执行步骤时调用")
    public String terminate(@ToolParam(required = false, description = "对完成状态的简短说明") String summary) {
        return "TERMINATED: " + (summary == null || summary.isBlank() ? "任务已完成" : summary.trim());
    }
}
