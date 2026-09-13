package com.lian.qingaiagent.agent;

/**
 * ReAct（Reasoning + Acting）抽象层：每轮先让模型决定是否需要行动，再执行工具调用。
 */
public abstract class ReActAgent extends BaseAgent {

    protected ReActAgent(String name, String systemPrompt, int maxSteps) {
        super(name, systemPrompt, maxSteps);
    }

    /** 根据当前消息和工具结果决定是否要调用工具。 */
    protected abstract boolean think();

    /** 执行本轮模型选择的工具。 */
    protected abstract String act();

    @Override
    protected String step() {
        boolean shouldAct = think();
        if (!shouldAct) {
            return "模型已经给出最终回答";
        }
        return act();
    }
}
