package com.lian.qingaiagent.agent.model;

import java.util.List;

/**
 * 规划阶段输出的轻量执行计划。
 *
 * <p>计划是给智能体执行循环使用的上下文，不是对用户承诺的严格工作流；真正执行时仍然
 * 要根据工具返回结果动态调整。</p>
 */
public record ExecutionPlan(String objective, List<String> steps) {

    public ExecutionPlan {
        objective = objective == null ? "" : objective.trim();
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
