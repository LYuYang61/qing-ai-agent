package com.lian.qingaiagent.agent;

import com.lian.qingaiagent.agent.model.ExecutionPlan;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * 通用 AI 超级智能体：先规划，再用 ReAct 循环组合本地工具和 MCP 工具完成任务。
 */
public class YuManus extends ToolCallAgent {

    private static final String SYSTEM_PROMPT = """
            你是 YuManus，一个面向复杂任务的 AI 超级智能体。
            你可以使用文件、网页、搜索、PDF、时间以及 MCP 远程工具。
            先理解目标和当前执行计划，再选择最合适的工具；工具返回结果后必须基于事实继续行动。
            需要多个工具时逐步执行，不要假设工具已经成功，也不要编造工具没有返回的数据。
            只有任务确实完成或无法继续时才调用 terminate；如果缺少关键用户信息，调用 askHuman 暂停并提问。
            不要把内部思维链原文输出给用户，只需要给出简洁的计划、工具事实和最终结论。
            """;

    private static final String NEXT_STEP_PROMPT = """
            根据用户目标和执行计划推进任务。每次工具调用后检查结果，必要时调整计划。
            对会改变工具参数的用户新要求必须重新调用相关工具，不能只凭上一轮记忆回答。
            任务完成后调用 terminate 工具；信息不足时调用 askHuman 工具。
            """;

    private final AgentProperties properties;

    public YuManus(ChatModel chatModel, ToolCallback[] allTools, AgentProperties properties) {
        this(chatModel, allTools, properties, SYSTEM_PROMPT);
    }

    protected YuManus(ChatModel chatModel, ToolCallback[] allTools, AgentProperties properties,
                      String systemPrompt) {
        super("yuManus", systemPrompt, properties.getMaxSteps(), properties.getDuplicateThreshold(),
                chatModel, allTools);
        this.properties = properties;
        setNextStepPrompt(NEXT_STEP_PROMPT);
    }

    @Override
    protected void beforeRun(String userPrompt) {
        if (!properties.isPlanningEnabled()) {
            setExecutionPlan(new ExecutionPlan(userPrompt, List.of("直接理解目标并通过 ReAct 工具循环完成任务")));
            return;
        }

        String plannerPrompt = """
                请为下面的用户任务生成执行计划。
                只输出不超过 %d 条简短的编号步骤，不要调用工具，不要输出思维链，不要添加与任务无关的解释。

                用户任务：%s
                """.formatted(properties.getMaxPlanSteps(), userPrompt);
        ChatResponse planResponse = getChatModel()
                // 不能传新建的 DashScopeChatOptions：其 multiModel 字段默认是非 null 的 false，
                // 合并时会覆盖全局 multi-model: true，把 flash 错误路由到文本端点（url error）。
                // 规划调用不需要工具，省略 options 让适配器直接使用模型默认配置。
                .call(new Prompt(List.of(
                        new SystemMessage("你是一个谨慎的任务规划器，计划必须可执行、可验证，并允许根据工具结果调整。"),
                        new UserMessage(plannerPrompt))));
        if (planResponse == null || planResponse.getResult() == null || planResponse.getResult().getOutput() == null) {
            throw new IllegalStateException("规划模型没有返回有效计划");
        }

        String rawPlan = planResponse.getResult().getOutput().getText();
        ExecutionPlan plan = ExecutionPlanParser.parse(userPrompt, rawPlan, properties.getMaxPlanSteps());
        setExecutionPlan(plan);
        setRuntimeInstructions("执行目标：" + plan.objective() + "\n计划步骤：\n- "
                + String.join("\n- ", plan.steps()));
    }
}
