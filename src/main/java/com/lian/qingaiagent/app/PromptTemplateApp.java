package com.lian.qingaiagent.app;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * PromptTemplate 示例：将业务提示词从 Java 逻辑中分离，并在运行时注入变量。
 */
@Component
@Profile("dashscope")
public class PromptTemplateApp {

    private final ChatClient chatClient;
    private final PromptTemplate adviceTemplate;

    public PromptTemplateApp(@Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel,
                             @Qualifier("promptChatMemory") ChatMemory chatMemory,
                             @Value("classpath:prompts/love-advice.st") Resource advicePrompt) {
        this.adviceTemplate = new PromptTemplate(advicePrompt);
        this.chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public String chat(String userName, String relationshipStatus, String tone,
                       String message, String conversationId) {
        // create(Map) 先完成模板渲染，再交给 ChatClient 叠加会话记忆并调用模型。
        Prompt prompt = adviceTemplate.create(Map.of(
                "userName", userName,
                "relationshipStatus", relationshipStatus,
                "tone", tone,
                "message", message));
        return chatClient.prompt(prompt)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    public String render(String userName, String relationshipStatus, String tone, String message) {
        // 暴露渲染结果便于初学者先观察“模板 -> 最终提示词”的变化，再调用模型。
        return adviceTemplate.render(Map.of(
                "userName", userName,
                "relationshipStatus", relationshipStatus,
                "tone", tone,
                "message", message));
    }
}
