package com.lian.qingaiagent.app;

import com.lian.qingaiagent.advisor.StudyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自定义应用：学习规划助手。
 *
 * <p>它复用本节学习的技术，但把业务从恋爱咨询替换成学习目标拆解和计划生成。</p>
 */
@Component
@Profile("dashscope")
public class StudyPlannerApp {

    private final ChatClient chatClient;

    public StudyPlannerApp(@Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel,
                           @Qualifier("studyChatMemory") ChatMemory chatMemory) {
        this.chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultSystem("你是一位严谨、务实的 Java 和 AI 学习规划助手。先澄清目标和时间约束，再给出可执行的学习计划。")
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new StudyLoggerAdvisor())
                .build();
    }

    public String chat(String message, String conversationId) {
        return chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    public StudyPlan createPlan(String message, String conversationId) {
        // 同样使用 entity(Class)，证明结构化输出并不绑定恋爱业务。
        return chatClient.prompt()
                .user("请根据下面的学习需求生成结构化计划：\n" + message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .entity(StudyPlan.class);
    }

    public record StudyPlan(String goal, int estimatedWeeks, List<String> milestones) {
    }
}
