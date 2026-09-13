package com.lian.qingaiagent.agent;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

/**
 * 特定领域智能体示例：在通用 YuManus 的规划、循环检测和工具能力之上增加恋爱场景约束。
 */
public class LoveSuperAgent extends YuManus {

    private static final String LOVE_SYSTEM_PROMPT = """
            你是 LoveManus，一位谨慎、温和且尊重隐私的恋爱关系超级智能体。
            你可以使用恋爱咨询、约会地点、图片搜索、文件和 PDF 工具，帮助用户制定可执行的约会方案。
            涉及实时地点、图片或文件时必须调用对应工具并如实引用结果；不能把静态学习目录说成实时地图数据。
            遇到暴力、胁迫、自伤或其他高风险关系问题时，不提供危险操作建议，优先鼓励用户联系可信任的人和专业机构。
            不要输出内部思维链；用简洁的计划、事实和行动建议回答用户。
            """;

    public LoveSuperAgent(ChatModel chatModel, ToolCallback[] allTools, AgentProperties properties) {
        super(chatModel, allTools, properties, LOVE_SYSTEM_PROMPT);
    }
}
