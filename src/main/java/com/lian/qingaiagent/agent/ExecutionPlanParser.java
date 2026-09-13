package com.lian.qingaiagent.agent;

import com.lian.qingaiagent.agent.model.ExecutionPlan;

import java.util.ArrayList;
import java.util.List;

/** 将规划模型的简短文本解析为可展示、可传递的执行计划。 */
public final class ExecutionPlanParser {

    private ExecutionPlanParser() {
    }

    public static ExecutionPlan parse(String objective, String rawPlan, int maxSteps) {
        List<String> steps = new ArrayList<>();
        if (rawPlan != null) {
            rawPlan.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .map(ExecutionPlanParser::removeListMarker)
                    .filter(line -> !line.isBlank())
                    .limit(Math.max(1, maxSteps))
                    .forEach(steps::add);
        }
        if (steps.isEmpty()) {
            steps.add("理解目标并根据工具结果逐步完成任务");
        }
        return new ExecutionPlan(objective, steps);
    }

    private static String removeListMarker(String line) {
        return line.replaceFirst("^(?:[-*•]|\\d+[.)、])\\s*", "").trim();
    }
}
