package com.lian.qingaiagent.chatmemory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Path;

/**
 * 为不同学习应用创建独立的会话记忆。
 *
 * <p>应用目录分开后，相同的 chatId 也不会把恋爱咨询和学习规划的上下文混在一起。</p>
 */
@Configuration
@Profile("dashscope")
@EnableConfigurationProperties(ChatMemoryProperties.class)
public class ChatMemoryConfiguration {

    @Bean("loveChatMemory")
    public ChatMemory loveChatMemory(ChatMemoryProperties properties) {
        return createChatMemory(properties, "love");
    }

    @Bean("studyChatMemory")
    public ChatMemory studyChatMemory(ChatMemoryProperties properties) {
        return createChatMemory(properties, "study");
    }

    @Bean("promptChatMemory")
    public ChatMemory promptChatMemory(ChatMemoryProperties properties) {
        return createChatMemory(properties, "prompt");
    }

    private ChatMemory createChatMemory(ChatMemoryProperties properties, String namespace) {
        ChatMemoryRepository repository = createRepository(properties, namespace);
        int maxMessages = properties.getMaxMessages();
        if (maxMessages < 2) {
            throw new IllegalArgumentException("qing.ai.chat-memory.max-messages 必须至少为 2");
        }
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(maxMessages)
                .build();
    }

    private ChatMemoryRepository createRepository(ChatMemoryProperties properties, String namespace) {
        if ("file".equalsIgnoreCase(properties.getBackend())) {
            return new FileBasedChatMemoryRepository(
                    Path.of(properties.getFileDir(), namespace));
        }
        if ("memory".equalsIgnoreCase(properties.getBackend())) {
            return new InMemoryChatMemoryRepository();
        }
        throw new IllegalArgumentException("不支持的对话记忆 backend: " + properties.getBackend()
                + "，可选值为 file 或 memory");
    }
}
