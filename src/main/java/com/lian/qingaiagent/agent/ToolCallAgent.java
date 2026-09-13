package com.lian.qingaiagent.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.lian.qingaiagent.tools.AskHumanTool;
import com.lian.qingaiagent.tools.TerminateTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 手动驱动 Spring AI 工具调用循环的 Agent。
 *
 * <p>Spring AI 1.1.2 默认可以自动托管工具调用，但超级智能体需要在每轮之间检查状态、检测
 * 重复调用和暂停等待用户。因此这里使用官方的用户控制模式：关闭模型内部工具执行，获取
 * {@link ChatResponse} 后交给 {@link ToolCallingManager} 执行，再把对话历史送回模型。</p>
 */
@Slf4j
public class ToolCallAgent extends ReActAgent {

    private final ChatModel chatModel;
    private final ToolCallback[] availableTools;
    private final ToolCallingManager toolCallingManager;
    private final ChatOptions chatOptions;
    private final int duplicateThreshold;

    private ChatResponse toolCallChatResponse;
    private String lastToolSignature = "";
    private int sameToolCallCount;
    private boolean stuckRecoveryUsed;
    private long cumulativePromptTokens;
    private long cumulativeCompletionTokens;

    public ToolCallAgent(String name, String systemPrompt, int maxSteps, int duplicateThreshold,
                         ChatModel chatModel, ToolCallback[] availableTools) {
        super(name, systemPrompt, maxSteps);
        if (duplicateThreshold < 2) {
            throw new IllegalArgumentException("重复调用阈值必须至少为 2");
        }
        this.chatModel = chatModel;
        this.availableTools = availableTools == null ? new ToolCallback[0] : availableTools.clone();
        this.duplicateThreshold = duplicateThreshold;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // DashScopeChatOptions 实现了 Spring AI 1.1.2 的 ToolCallingChatOptions；关闭内部执行后，
        // 每一轮 ChatResponse 都会回到本类，由本类决定是否执行、暂停或结束。
        this.chatOptions = baseOptionsBuilder()
                .toolCallbacks(Arrays.asList(this.availableTools))
                .internalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 以模型默认配置为底构建选项。
     *
     * <p>新建的 DashScopeChatOptions 的 multiModel 默认是非 null 的 false，合并时会覆盖
     * 全局 multi-model: true，导致 flash 被路由到文本端点报 url error；路由字段必须从模型
     * 默认配置显式复制（实测见 2026-09-13 T1 排错）。</p>
     */
    private DashScopeChatOptions.DashScopeChatOptionsBuilder baseOptionsBuilder() {
        DashScopeChatOptions.DashScopeChatOptionsBuilder builder = DashScopeChatOptions.builder();
        if (chatModel.getDefaultOptions() instanceof DashScopeChatOptions modelDefaults) {
            builder.model(modelDefaults.getModel()).multiModel(modelDefaults.getMultiModel());
        }
        return builder;
    }

    /**
     * 步数预算耗尽时的确定性收尾：执行一次不带任何工具的模型调用，强制基于已有上下文
     * 输出最终成果总结。软提示无法保证模型主动 terminate（实测 18/18 仍在验证性调用），
     * 收尾必须由结构兜底——与工具描述治理的结论一致。
     */
    @Override
    protected String onBudgetExhausted() {
        log.info("Agent {} 步数预算耗尽，执行无工具收尾总结", getName());
        try {
            String system = getSystemPrompt()
                    + "\n\n任务执行已到达步数上限，本轮禁止调用任何工具。"
                    + "请立即基于已有对话内容输出最终成果总结：列出已完成的步骤、产物和结论。";
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(system));
            messages.addAll(getMessageList());
            ChatResponse response = getChatModel()
                    .call(new Prompt(messages, baseOptionsBuilder().build()));
            logUsage("收尾", response);
            String summary = response.getResult().getOutput().getText();
            return "（已达最大执行步数 " + getMaxSteps() + "，由系统强制收尾）\n"
                    + (summary == null || summary.isBlank() ? super.onBudgetExhausted() : summary);
        }
        catch (Exception exception) {
            log.warn("Agent {} 收尾总结失败，回退默认提示", getName(), exception);
            return super.onBudgetExhausted();
        }
    }

    @Override
    protected boolean think() {
        String nextPrompt = consumeNextStepPrompt();
        if (nextPrompt != null && !nextPrompt.isBlank()) {
            addMessage(new UserMessage(nextPrompt));
        }

        ChatResponse response = chatModel.call(new Prompt(buildModelMessages(), chatOptions));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("模型没有返回有效的 AssistantMessage");
        }
        logUsage("think", response);

        toolCallChatResponse = response;
        AssistantMessage assistantMessage = response.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
        log.info("Agent {} selected {} tool calls: {}", getName(), toolCalls.size(),
                toolCalls.stream().map(AssistantMessage.ToolCall::name).collect(Collectors.joining(", ")));

        if (!assistantMessage.hasToolCalls()) {
            addMessage(assistantMessage);
            finish(assistantMessage.getText());
            return false;
        }
        // ToolCallingManager 会把包含工具调用的 AssistantMessage 和 ToolResponseMessage 一起放入历史，
        // 所以这里不能提前把 assistantMessage 手工追加一次，否则下一轮会出现重复消息。
        return true;
    }

    @Override
    protected String act() {
        if (toolCallChatResponse == null || !toolCallChatResponse.hasToolCalls()) {
            finish("模型没有提出工具调用。");
            return "没有工具需要执行";
        }

        Prompt toolPrompt = new Prompt(new ArrayList<>(getMessageList()), chatOptions);
        ToolExecutionResult executionResult = toolCallingManager.executeToolCalls(toolPrompt, toolCallChatResponse);
        replaceMessageList(executionResult.conversationHistory());

        ToolResponseMessage toolResponseMessage = findLastToolResponse(executionResult.conversationHistory());
        if (toolResponseMessage == null) {
            throw new IllegalStateException("工具执行后没有返回 ToolResponseMessage");
        }

        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 返回：" + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info("Agent {} tool results: {}", getName(), results);

        String humanQuestion = toolResponseMessage.getResponses().stream()
                .filter(response -> AskHumanTool.TOOL_NAME.equals(response.name()))
                .map(response -> AskHumanTool.questionFromMarker(response.responseData()))
                .filter(question -> question != null && !question.isBlank())
                .findFirst()
                .orElse(null);
        if (humanQuestion != null) {
            waitForUser(humanQuestion);
            return results;
        }

        boolean terminated = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> TerminateTool.TOOL_NAME.equals(response.name()));
        if (terminated) {
            finish("任务已由终止工具确认完成。\n" + results);
            return results;
        }

        if (executionResult.returnDirect()) {
            finish(results);
            return results;
        }

        String signature = buildToolCallSignature(toolCallChatResponse);
        if (isStuck(signature)) {
            if (stuckRecoveryUsed) {
                finish("检测到连续重复的工具调用，已停止循环以保护执行预算。\n" + results);
            }
            else {
                handleStuckState();
            }
        }
        return results;
    }

    private List<Message> buildModelMessages() {
        List<Message> messages = new ArrayList<>();
        String system = getSystemPrompt();
        if (getRuntimeInstructions() != null && !getRuntimeInstructions().isBlank()) {
            system = system + "\n\n运行时执行约束：\n" + getRuntimeInstructions();
        }
        // 系统消息每轮重建、不进入会话历史，是注入步数预算的最佳位置：
        // 让模型始终知道还剩多少步，避免研究阶段耗尽预算、产出阶段无步可用
        // （实测 2026-09-13 T2：12 步全部耗在搜索/抓取，配图与 PDF 一步未执行）。
        system = system + "\n\n当前进度：第 " + getCurrentStep() + " 步 / 共 " + getMaxSteps()
                + " 步，剩余 " + (getMaxSteps() - getCurrentStep()) + " 步。"
                + "若剩余步数不足以完成全部计划，立即停止扩展研究，基于已有信息产出最终成果并调用 terminate。";
        messages.add(new SystemMessage(system));
        messages.addAll(getMessageList());
        return messages;
    }

    private ToolResponseMessage findLastToolResponse(List<Message> history) {
        if (history == null) {
            return null;
        }
        for (int index = history.size() - 1; index >= 0; index--) {
            if (history.get(index) instanceof ToolResponseMessage responseMessage) {
                return responseMessage;
            }
        }
        return null;
    }

    private String buildToolCallSignature(ChatResponse response) {
        return response.getResult().getOutput().getToolCalls().stream()
                .map(call -> call.name() + "(" + call.arguments() + ")")
                .collect(Collectors.joining("|"));
    }

    /** 连续重复相同工具和参数时返回 true；不同调用会重置恢复窗口。 */
    protected boolean isStuck(String signature) {
        if (signature.equals(lastToolSignature)) {
            sameToolCallCount++;
        }
        else {
            lastToolSignature = signature;
            sameToolCallCount = 1;
            stuckRecoveryUsed = false;
        }
        return sameToolCallCount >= duplicateThreshold;
    }

    /** 第一次发现循环时插入一次策略修正提示；第二次仍重复时由 act() 终止。 */
    protected void handleStuckState() {
        stuckRecoveryUsed = true;
        setNextStepPrompt("系统检测到你连续重复相同的工具调用。请检查上一次工具结果，换一种策略；如果任务已经完成，请调用 terminate 工具结束任务。");
        log.warn("Agent {} detected repeated tool calls and requested a strategy change", getName());
    }

    public ToolCallback[] getAvailableTools() {
        return availableTools.clone();
    }

    protected ChatModel getChatModel() {
        return chatModel;
    }

    /**
     * 记录单步与累计 token 消耗。
     *
     * <p>Agent 使用裸 ChatModel（无 advisor 日志），用量必须就地打印才能看见；
     * 相邻两步 prompt 用量的差值即为该步新进入历史的体积，是定位"哪一步内容最费钱"的直接依据。</p>
     */
    private void logUsage(String phase, ChatResponse response) {
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage == null) {
            return;
        }
        Integer promptTokens = usage.getPromptTokens();
        Integer completionTokens = usage.getCompletionTokens();
        cumulativePromptTokens += promptTokens == null ? 0 : promptTokens;
        cumulativeCompletionTokens += completionTokens == null ? 0 : completionTokens;
        log.info("Agent {} [{}] token 用量：本步 prompt={}, completion={}；累计 prompt={}, completion={}",
                getName(), phase, promptTokens, completionTokens,
                cumulativePromptTokens, cumulativeCompletionTokens);
    }
}
