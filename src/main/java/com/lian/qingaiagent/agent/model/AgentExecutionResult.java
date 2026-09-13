package com.lian.qingaiagent.agent.model;

import java.util.List;

/**
 * 智能体接口返回的可观察结果。
 *
 * <p>相比教程中只返回 String，这个对象额外暴露状态、步骤和待回答问题，便于 HTTP 客户端
 * 判断是任务完成、遇到错误，还是需要调用 resume 接口继续执行。</p>
 */
public record AgentExecutionResult(
        AgentState state,
        String answer,
        String pendingQuestion,
        int currentStep,
        int maxSteps,
        ExecutionPlan plan,
        List<String> stepResults) {

    public AgentExecutionResult {
        state = state == null ? AgentState.ERROR : state;
        answer = answer == null ? "" : answer;
        pendingQuestion = pendingQuestion == null ? "" : pendingQuestion;
        plan = plan == null ? new ExecutionPlan("", List.of()) : plan;
        stepResults = stepResults == null ? List.of() : List.copyOf(stepResults);
    }

    public boolean waitingForUser() {
        return state == AgentState.WAITING_FOR_USER;
    }

    public boolean terminal() {
        return state == AgentState.FINISHED || state == AgentState.ERROR;
    }
}
