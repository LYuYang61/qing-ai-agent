package com.lian.qingaiagent.agent.model;

/**
 * 智能体一次执行过程的状态。
 *
 * <p>PLANNING 和 WAITING_FOR_USER 是普通 RUNNING 状态之外的两个重要阶段：前者表示
 * 正在生成执行计划，后者表示智能体主动暂停，等待用户补充信息，而不是把线程阻塞在控制台输入上。</p>
 */
public enum AgentState {

    /** 尚未执行。 */
    IDLE,

    /** 正在生成或整理执行计划。 */
    PLANNING,

    /** 正在进行 ReAct 循环。 */
    RUNNING,

    /** 已向用户提问，等待通过 resume 接口继续。 */
    WAITING_FOR_USER,

    /** 已完成。 */
    FINISHED,

    /** 执行过程中发生不可恢复错误。 */
    ERROR
}
