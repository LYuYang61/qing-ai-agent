package com.lian.qingaiagent.agent;

import com.lian.qingaiagent.agent.model.AgentExecutionResult;
import com.lian.qingaiagent.agent.model.AgentState;
import com.lian.qingaiagent.agent.model.ExecutionPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能体基类：负责状态、消息上下文和最大步骤循环。
 *
 * <p>它不关心模型和工具的具体实现，把单步动作交给子类。每个 {@code YuManus} 实例只允许
 * 执行一次新的任务；HTTP 层通过工厂为每个运行创建独立实例，避免多个用户共享可变消息列表。</p>
 */
@Slf4j
public abstract class BaseAgent {

    private final String name;
    private final String systemPrompt;
    private final int maxSteps;

    private AgentState state = AgentState.IDLE;
    private int currentStep;
    private final List<Message> messageList = new ArrayList<>();
    private final List<String> stepResults = new ArrayList<>();
    private String nextStepPrompt;
    private String runtimeInstructions = "";
    private String finalAnswer = "";
    private String pendingQuestion = "";
    private ExecutionPlan executionPlan = new ExecutionPlan("", List.of());

    protected BaseAgent(String name, String systemPrompt, int maxSteps) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("智能体名称不能为空");
        }
        if (maxSteps < 1) {
            throw new IllegalArgumentException("智能体最大步骤必须大于 0");
        }
        this.name = name;
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        this.maxSteps = maxSteps;
    }

    /**
     * 启动一次新任务。
     *
     * <p>先执行可选的规划钩子，再进入 ReAct 循环；发生错误时返回 ERROR 结果，而不是把部分
     * 状态悄悄当作成功答案交给 HTTP 调用方。</p>
     */
    public final AgentExecutionResult run(String userPrompt) {
        if (state != AgentState.IDLE) {
            throw new IllegalStateException("不能从状态 " + state + " 启动新的智能体任务");
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("智能体任务不能为空");
        }

        messageList.add(new UserMessage(userPrompt.trim()));
        state = AgentState.PLANNING;
        try {
            beforeRun(userPrompt.trim());
            state = AgentState.RUNNING;
            return executeLoop();
        }
        catch (Exception exception) {
            return fail(exception);
        }
    }

    /**
     * 用户回答 askHuman 工具提出的问题后，继续同一个运行上下文。
     */
    public final AgentExecutionResult resume(String userInput) {
        if (state != AgentState.WAITING_FOR_USER) {
            throw new IllegalStateException("当前任务不在等待用户输入状态：" + state);
        }
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("用户补充信息不能为空");
        }
        pendingQuestion = "";
        messageList.add(new UserMessage("用户对智能体问题的补充：\n" + userInput.trim()));
        state = AgentState.RUNNING;
        try {
            return executeLoop();
        }
        catch (Exception exception) {
            return fail(exception);
        }
    }

    private AgentExecutionResult executeLoop() {
        while (state == AgentState.RUNNING && currentStep < maxSteps) {
            currentStep++;
            log.info("Agent {} executing step {}/{}", name, currentStep, maxSteps);
            String stepResult = step();
            stepResults.add("第 " + currentStep + " 步：" + (stepResult == null ? "" : stepResult));
        }

        if (state == AgentState.RUNNING) {
            state = AgentState.FINISHED;
            if (finalAnswer.isBlank()) {
                finalAnswer = onBudgetExhausted();
            }
        }
        return snapshot();
    }

    /**
     * 步数预算耗尽且模型未主动 terminate 时的收尾策略。
     *
     * <p>默认返回固定提示；子类可覆盖为一次无工具的总结调用，让用户拿到成果总结而不是
     * 一句兜底文案（实测 2026-09-13 T2：任务实质完成，但 18/18 耗尽后最终响应只剩
     * "达到最大执行步骤"，产物与结论都对用户不可见）。</p>
     */
    protected String onBudgetExhausted() {
        return "智能体达到最大执行步骤（" + maxSteps + "），已停止继续调用工具。";
    }

    private AgentExecutionResult fail(Exception exception) {
        state = AgentState.ERROR;
        finalAnswer = "智能体执行失败：" + (exception.getMessage() == null
                ? exception.getClass().getSimpleName() : exception.getMessage());
        log.error("Agent {} execution failed", name, exception);
        return snapshot();
    }

    private AgentExecutionResult snapshot() {
        String answer = finalAnswer;
        if (answer == null || answer.isBlank()) {
            answer = state == AgentState.WAITING_FOR_USER
                    ? pendingQuestion : "智能体没有生成可展示的文本结果。";
        }
        return new AgentExecutionResult(state, answer, pendingQuestion, currentStep, maxSteps,
                executionPlan, stepResults);
    }

    /** 规划或初始化运行上下文的钩子，默认不做额外工作。 */
    protected void beforeRun(String userPrompt) {
    }

    /** 执行一次“思考—行动”步骤。 */
    protected abstract String step();

    protected String getName() {
        return name;
    }

    protected String getSystemPrompt() {
        return systemPrompt;
    }

    protected List<Message> getMessageList() {
        return messageList;
    }

    protected void replaceMessageList(List<Message> messages) {
        messageList.clear();
        if (messages != null) {
            messageList.addAll(messages);
        }
    }

    protected String consumeNextStepPrompt() {
        String prompt = nextStepPrompt;
        nextStepPrompt = null;
        return prompt;
    }

    protected void setNextStepPrompt(String nextStepPrompt) {
        this.nextStepPrompt = nextStepPrompt;
    }

    protected String getRuntimeInstructions() {
        return runtimeInstructions;
    }

    protected void setRuntimeInstructions(String runtimeInstructions) {
        this.runtimeInstructions = runtimeInstructions == null ? "" : runtimeInstructions;
    }

    protected void setExecutionPlan(ExecutionPlan executionPlan) {
        this.executionPlan = executionPlan == null ? new ExecutionPlan("", List.of()) : executionPlan;
    }

    protected void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer == null ? "" : finalAnswer.trim();
    }

    protected void finish(String answer) {
        setFinalAnswer(answer);
        state = AgentState.FINISHED;
    }

    protected void waitForUser(String question) {
        pendingQuestion = question == null || question.isBlank()
                ? "请补充完成任务所需的信息。" : question.trim();
        state = AgentState.WAITING_FOR_USER;
    }

    protected void addMessage(Message message) {
        if (message != null) {
            messageList.add(message);
        }
    }

    public AgentState getState() {
        return state;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public ExecutionPlan getExecutionPlan() {
        return executionPlan;
    }
}
