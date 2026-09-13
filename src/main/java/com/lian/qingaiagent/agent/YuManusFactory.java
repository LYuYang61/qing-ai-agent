package com.lian.qingaiagent.agent;

import com.lian.qingaiagent.tools.AskHumanTool;
import com.lian.qingaiagent.tools.TerminateTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 为每次 HTTP 运行创建独立 Agent。
 *
 * <p>本地工具、人工交互工具和 MCP 工具最后都变成同一种 {@link ToolCallback}，因此 Agent
 * 的 ReAct 循环不需要区分工具来源。</p>
 */
@Slf4j
@Component
@Profile("dashscope")
public class YuManusFactory {

    private final ChatModel chatModel;
    private final ToolCallback[] localTools;
    private final ObjectProvider<SyncMcpToolCallbackProvider> mcpProvider;
    private final AgentProperties properties;

    public YuManusFactory(@Qualifier("dashScopeChatModel") ChatModel chatModel,
                          @Qualifier("loveToolCallbacks") ToolCallback[] localTools,
                          ObjectProvider<SyncMcpToolCallbackProvider> mcpProvider,
                          AgentProperties properties) {
        this.chatModel = chatModel;
        this.localTools = localTools.clone();
        this.mcpProvider = mcpProvider;
        this.properties = properties;
        validateProperties(properties);
    }

    public YuManus createGeneralAgent() {
        return new YuManus(chatModel, allTools(), properties);
    }

    public LoveSuperAgent createLoveAgent() {
        return new LoveSuperAgent(chatModel, allTools(), properties);
    }

    private ToolCallback[] allTools() {
        List<ToolCallback> callbacks = new ArrayList<>(Arrays.asList(localTools));
        callbacks.addAll(Arrays.asList(ToolCallbacks.from(new AskHumanTool(), new TerminateTool())));

        SyncMcpToolCallbackProvider provider = mcpProvider.getIfAvailable();
        if (provider != null) {
            // MCP Provider 的发现结果与本地 ToolCallback 使用同一接口，可直接合并给手动循环。
            ToolCallback[] mcpTools = provider.getToolCallbacks();
            callbacks.addAll(Arrays.asList(mcpTools));
            log.info("YuManus discovered {} MCP tools", mcpTools.length);
        }
        return callbacks.toArray(ToolCallback[]::new);
    }

    private void validateProperties(AgentProperties agentProperties) {
        if (agentProperties.getMaxSteps() < 1 || agentProperties.getDuplicateThreshold() < 2
                || agentProperties.getMaxPlanSteps() < 1 || agentProperties.getMaxActiveRuns() < 1
                || agentProperties.getRunTtl().isZero() || agentProperties.getRunTtl().isNegative()) {
            throw new IllegalArgumentException("qing.ai.agent 配置必须使用正数执行限制和正的 run-ttl");
        }
    }
}
