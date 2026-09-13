package com.lian.qingaiagent.controller;

import com.lian.qingaiagent.agent.AgentRunStore;
import com.lian.qingaiagent.agent.LoveSuperAgent;
import com.lian.qingaiagent.agent.YuManus;
import com.lian.qingaiagent.agent.YuManusFactory;
import com.lian.qingaiagent.agent.model.AgentExecutionResult;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;
import java.util.UUID;

/** 第八期 AI 超级智能体接口；仅在 dashscope Profile 下开放。 */
@RestController
@Profile("dashscope")
@RequestMapping("/ai/agent")
public class AgentApplicationController {

    private final YuManusFactory agentFactory;
    private final AgentRunStore runStore;

    public AgentApplicationController(YuManusFactory agentFactory, AgentRunStore runStore) {
        this.agentFactory = agentFactory;
        this.runStore = runStore;
    }

    /** 通用超级智能体：组合本地工具和当前 Profile 可用的 MCP 工具。 */
    @PostMapping("/run")
    public AgentRunResponse run(@RequestParam String prompt) {
        return start(prompt, agentFactory.createGeneralAgent());
    }

    /** 恋爱领域超级智能体：复用同一执行引擎，但使用更严格的领域系统提示词。 */
    @PostMapping("/love/run")
    public AgentRunResponse runLove(@RequestParam String prompt) {
        return start(prompt, agentFactory.createLoveAgent());
    }

    /** 在 askHuman 工具暂停后继续同一个智能体上下文。 */
    @PostMapping("/resume")
    public AgentRunResponse resume(@RequestParam String runId, @RequestParam String userInput) {
        YuManus agent;
        try {
            agent = runStore.get(runId);
        }
        catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
        return finishOrKeep(runId, agent.resume(userInput));
    }

    private AgentRunResponse start(String prompt, YuManus agent) {
        if (prompt == null || prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prompt 不能为空");
        }
        String runId = UUID.randomUUID().toString();
        runStore.put(runId, agent);
        try {
            return finishOrKeep(runId, agent.run(prompt));
        }
        catch (RuntimeException exception) {
            // 参数校验或配置错误不应留下一个永远无法恢复的运行记录。
            runStore.remove(runId);
            throw exception;
        }
    }

    private AgentRunResponse finishOrKeep(String runId, AgentExecutionResult result) {
        if (!result.waitingForUser() || result.terminal()) {
            runStore.remove(runId);
        }
        return new AgentRunResponse(runId, result);
    }

    public record AgentRunResponse(String runId, AgentExecutionResult result) {
    }
}
