package com.lian.qingaiagent.app;

import com.lian.qingaiagent.advisor.ReReadingAdvisor;
import com.lian.qingaiagent.advisor.StudyLoggerAdvisor;
import com.lian.qingaiagent.rag.LoveRagFilterFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 恋爱大师应用：演示系统提示词、多轮记忆、Advisor、流式输出和结构化输出。
 */
@Component
@Profile("dashscope")
public class LoveApp {

    private static final String SYSTEM_PROMPT = """
            你是一位温和、理性且尊重隐私的恋爱关系顾问，称为“恋爱大师”。
            用户可以向你倾诉单身、恋爱或婚姻中的问题。
            先理解用户的处境，再给出具体、可执行且不带评判的建议。
            不要替用户做危险或极端的决定；遇到暴力、胁迫、自伤等风险时，优先建议联系可信任的人和专业机构。
            """;

    private final ChatClient chatClient;

    private final ObjectProvider<Advisor> localFaqRagAdvisorProvider;

    private final ToolCallback[] toolCallbacks;

    public LoveApp(@Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel,
                   @Qualifier("loveChatMemory") ChatMemory chatMemory,
                   @Qualifier("loveAppLocalFaqRagAdvisor") ObjectProvider<Advisor> localFaqRagAdvisorProvider,
                   @Qualifier("loveToolCallbacks") ToolCallback[] toolCallbacks) {
        // ChatMemory 只负责保存消息；MessageChatMemoryAdvisor 负责把历史消息注入下一次请求。
        // 具体使用内存还是文件，由 qing.ai.chat-memory.backend 配置决定。
        this.chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new StudyLoggerAdvisor())
                .build();
        this.localFaqRagAdvisorProvider = localFaqRagAdvisorProvider;
        this.toolCallbacks = toolCallbacks;
    }

    public String chat(String message, String conversationId) {
        return chatClient.prompt()
                .user(message)
                // 使用相同会话 ID 才能读取同一段对话记忆。
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    public Flux<String> stream(String message, String conversationId) {
        return chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    public LoveReport chatWithReport(String message, String conversationId) {
        // entity(LoveReport.class) 让 Spring AI 负责把模型文本转换成 Java record。
        return chatClient.prompt()
                .system(SYSTEM_PROMPT + "\n请在回答中生成一份结构化恋爱报告。标题简洁，建议必须是可执行的列表。")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .entity(LoveReport.class);
    }

    public String chatWithReReading(String message, String conversationId) {
        // Advisor 可以按次加入，只影响这一轮请求，不改变默认 Advisor 链。
        return chatClient.prompt()
                .user(message)
                .advisors(spec -> spec
                        .param(ChatMemory.CONVERSATION_ID, conversationId)
                        .advisors(new ReReadingAdvisor()))
                .call()
                .content();
    }

    /**
     * 在原有多轮对话链路上增加本地 FAQ RAG。
     *
     * <p>RAG Advisor 只在本次请求中加入，普通恋爱对话不会自动检索知识库；ChatMemory Advisor
     * 仍然负责读取和保存同一个 conversationId 的历史消息。</p>
     */
    public String chatWithRag(String message, String conversationId, String status) {
        Advisor ragAdvisor = localFaqRagAdvisorProvider.getIfAvailable();
        if (ragAdvisor == null) {
            throw new IllegalStateException("本地 RAG 未启用，请设置 qing.ai.rag.local.enabled=true");
        }
        return chatClient.prompt()
                .user(message)
                .advisors(spec -> spec
                        .param(ChatMemory.CONVERSATION_ID, conversationId)
                        .advisors(ragAdvisor)
                        // 通过请求参数覆盖 Advisor 的默认过滤条件，按关系状态缩小 FAQ 检索范围。
                        .param(VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                                LoveRagFilterFactory.faq(status)))
                .call()
                .content();
    }

    /**
     * 使用本节集中注册的工具完成恋爱相关任务。
     *
     * <p>这里使用 {@code toolCallbacks} 而不是把工具对象逐个写进业务方法；Spring AI 会在模型提出
     * 工具调用后自动执行回调、把结果放回对话，并继续请求模型生成最终回答。会话 ID 仍由 ChatMemory
     * Advisor 管理，因此一次工具调用的过程也能延续到下一轮对话。</p>
     */
    public String chatWithTools(String message, String conversationId) {
        return chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .toolCallbacks(toolCallbacks)
                .call()
                .content();
    }

    public record LoveReport(String title, List<String> suggestions) {
    }
}
