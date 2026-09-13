package com.lian.qingaiagent.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.lian.qingaiagent.agent.model.AgentState;
import com.lian.qingaiagent.agent.model.AgentExecutionResult;
import com.lian.qingaiagent.tools.AskHumanTool;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentCoreTest {

    @Test
    void manuallyExecutesToolThenContinuesWithFinalAnswer() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
                response(toolCall("call-1", "echo", "{\"value\":\"hello\"}")),
                response(new AssistantMessage("工具结果已经整理完成")));

        ToolCallback[] tools = ToolCallbacks.from(new EchoTool());
        ToolCallAgent agent = new ToolCallAgent("test", "你是测试 Agent", 5, 2, chatModel, tools);

        AgentExecutionResult result = agent.run("调用工具处理 hello");

        assertEquals(AgentState.FINISHED, result.state());
        assertEquals("工具结果已经整理完成", result.answer());
        assertEquals(2, result.currentStep());
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    /**
     * 回归测试（2026-09-13 T1 排错）：新建 DashScopeChatOptions 的 multiModel 默认非 null false，
     * 曾覆盖全局 multi-model: true 导致 flash 走文本端点报 url error。
     * 此处断言 think() 发出的 Prompt 保留模型默认的路由字段，且内部执行处于关闭状态。
     */
    @Test
    void preservesModelRoutingFromModelDefaults() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(DashScopeChatOptions.builder()
                .model("qwen3.7-flash")
                .multiModel(true)
                .build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
                response(new AssistantMessage("直接回答，无需工具")));

        ToolCallAgent agent = new ToolCallAgent("test", "你是测试 Agent", 3, 2,
                chatModel, ToolCallbacks.from(new EchoTool()));
        agent.run("测试路由字段");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        DashScopeChatOptions sent = (DashScopeChatOptions) captor.getValue().getOptions();
        assertEquals("qwen3.7-flash", sent.getModel());
        assertEquals(Boolean.TRUE, sent.getMultiModel());
        assertEquals(Boolean.FALSE, sent.getInternalToolExecutionEnabled());
        assertTrue(sent.getToolCallbacks() != null && !sent.getToolCallbacks().isEmpty());
        // 步数预算注入：系统消息每轮携带进度与剩余步数（T2 预算耗尽问题的回归断言）。
        String systemText = captor.getValue().getInstructions().get(0).getText();
        assertTrue(systemText.contains("第 1 步 / 共 3 步，剩余 2 步"));
    }

    @Test
    void askHumanPausesAndResumeContinuesSameConversation() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
                response(toolCall("call-human", AskHumanTool.TOOL_NAME,
                        "{\"question\":\"请提供预算\"}")),
                response(new AssistantMessage("已根据预算完成计划")));

        ToolCallback[] tools = ToolCallbacks.from(new AskHumanTool());
        ToolCallAgent agent = new ToolCallAgent("test", "你是测试 Agent", 5, 2, chatModel, tools);

        AgentExecutionResult waiting = agent.run("制定约会计划");
        assertEquals(AgentState.WAITING_FOR_USER, waiting.state());
        assertEquals("请提供预算", waiting.pendingQuestion());

        AgentExecutionResult resumed = agent.resume("预算 200 元");
        assertEquals(AgentState.FINISHED, resumed.state());
        assertEquals("已根据预算完成计划", resumed.answer());
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void repeatedToolCallsTriggerRecoveryThenStop() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse repeatedCall = response(toolCall("call-loop", "echo", "{\"value\":\"same\"}"));
        when(chatModel.call(any(Prompt.class))).thenReturn(repeatedCall, repeatedCall, repeatedCall);

        ToolCallAgent agent = new ToolCallAgent("test", "你是测试 Agent", 5, 2,
                chatModel, ToolCallbacks.from(new EchoTool()));

        AgentExecutionResult result = agent.run("测试循环检测");

        assertEquals(AgentState.FINISHED, result.state());
        assertTrue(result.answer().contains("重复"));
        assertEquals(3, result.currentStep());
        verify(chatModel, times(3)).call(any(Prompt.class));
    }

    /**
     * 回归测试（2026-09-13 T2 排错）：步数预算耗尽且模型未主动 terminate 时，
     * 系统应执行一次无工具的强制收尾总结，而不是只返回固定兜底文案。
     * 三步工具调用参数各不相同，避免触发卡死检测。
     */
    @Test
    void budgetExhaustionTriggersToolFreeWrapUp() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
                response(toolCall("call-1", "echo", "{\"value\":\"a\"}")),
                response(toolCall("call-2", "echo", "{\"value\":\"b\"}")),
                response(toolCall("call-3", "echo", "{\"value\":\"c\"}")),
                response(new AssistantMessage("已完成信息收集并整理出最终结论。")));

        ToolCallAgent agent = new ToolCallAgent("test", "你是测试 Agent", 3, 2,
                chatModel, ToolCallbacks.from(new EchoTool()));

        AgentExecutionResult result = agent.run("测试预算收尾");

        assertEquals(AgentState.FINISHED, result.state());
        assertTrue(result.answer().contains("由系统强制收尾"));
        assertTrue(result.answer().contains("已完成信息收集并整理出最终结论"));
        verify(chatModel, times(4)).call(any(Prompt.class));
    }

    @Test
    void parserKeepsNumberedPlanStepsWithinConfiguredLimit() {
        var plan = ExecutionPlanParser.parse("测试目标", "1. 查资料\n2、整理结果\n3. 输出报告", 2);

        assertEquals(List.of("查资料", "整理结果"), plan.steps());
    }

    private static ChatResponse response(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static AssistantMessage toolCall(String id, String name, String arguments) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", name, arguments)))
                .build();
    }

    public static class EchoTool {

        @Tool(name = "echo", description = "回显用户提供的文本")
        public String echo(@ToolParam(description = "要回显的文本") String value) {
            return "echo:" + value;
        }
    }
}
