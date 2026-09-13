package com.lian.qingaiagent.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 超级智能体的运行保护参数。
 *
 * <p>这些参数不应该硬编码在循环中：最大步数限制成本，重复检测限制异常循环，活动运行数和
 * TTL 限制 HTTP 服务中等待人工输入的状态数量。</p>
 */
@ConfigurationProperties(prefix = "qing.ai.agent")
public class AgentProperties {

    /** 单次运行最多请求模型并执行工具的轮数；需为"研究 + 产出"两阶段都留出预算。 */
    private int maxSteps = 18;

    /** 连续重复相同工具调用达到该次数后触发一次恢复提示。 */
    private int duplicateThreshold = 2;

    /** 是否在 ReAct 循环前增加一次规划模型调用。 */
    private boolean planningEnabled = true;

    /** 规划器最多保留的步骤数量。 */
    private int maxPlanSteps = 6;

    /** 同时保存在内存中的人工交互运行数上限。 */
    private int maxActiveRuns = 20;

    /** 人工交互运行的空闲保留时间。 */
    private Duration runTtl = Duration.ofMinutes(30);

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public int getDuplicateThreshold() {
        return duplicateThreshold;
    }

    public void setDuplicateThreshold(int duplicateThreshold) {
        this.duplicateThreshold = duplicateThreshold;
    }

    public boolean isPlanningEnabled() {
        return planningEnabled;
    }

    public void setPlanningEnabled(boolean planningEnabled) {
        this.planningEnabled = planningEnabled;
    }

    public int getMaxPlanSteps() {
        return maxPlanSteps;
    }

    public void setMaxPlanSteps(int maxPlanSteps) {
        this.maxPlanSteps = maxPlanSteps;
    }

    public int getMaxActiveRuns() {
        return maxActiveRuns;
    }

    public void setMaxActiveRuns(int maxActiveRuns) {
        this.maxActiveRuns = maxActiveRuns;
    }

    public Duration getRunTtl() {
        return runTtl;
    }

    public void setRunTtl(Duration runTtl) {
        this.runTtl = runTtl;
    }
}
