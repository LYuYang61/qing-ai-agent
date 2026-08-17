package com.lian.qingaiagent.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 使用百炼云知识库完成恋爱知识问答。 */
@Component
@Profile("dashscope")
@ConditionalOnProperty(prefix = "qing.ai.rag.cloud", name = "enabled", havingValue = "true")
public class CloudLoveRagApp {

    private static final String SYSTEM_PROMPT = """
            你是恋爱知识库问答助手，只能依据云知识库检索到的资料回答问题。
            如果资料不足，请明确说明，不要编造知识库中不存在的事实。
            """;

    private final ChatClient chatClient;

    public CloudLoveRagApp(
            @Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel,
            @Qualifier("loveAppCloudRagAdvisor") Advisor cloudRagAdvisor) {
        this.chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(cloudRagAdvisor)
                .build();
    }

    public String chat(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
