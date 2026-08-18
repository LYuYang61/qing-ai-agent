package com.lian.qingaiagent.rag;

import com.lian.qingaiagent.advisor.StudyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于本地向量知识库的恋爱问答和恋爱对象推荐应用。
 *
 * <p>同一个 ChatClient 可以挂载不同的 RAG Advisor：问答链只检索 FAQ，推荐链只检索候选人资料，
 * 这样数据边界由元数据过滤保证，而不是依赖 Prompt 文字提醒。</p>
 */
@Component
@Profile("dashscope")
@ConditionalOnProperty(prefix = "qing.ai.rag.local", name = "enabled", havingValue = "true")
public class LoveRagApp {

    private static final String FAQ_SYSTEM_PROMPT = """
            你是恋爱知识库问答助手。
            只能基于 RAG 检索到的恋爱资料回答，资料不足时明确说明，不要编造来源。
            回答要温和、具体、尊重隐私；遇到暴力、胁迫、自伤等风险时优先建议联系可信任的人和专业机构。
            """;

    private static final String MATCH_SYSTEM_PROMPT = """
            你是恋爱对象匹配助手。
            只能根据检索到的候选人资料分析匹配度，不得补写资料中没有的个人信息。
            如果没有足够合适的候选人，matches 返回空数组，并在 summary 中说明原因。
            输出必须符合请求的结构化格式，matchScore 使用 0 到 100 的整数。
            """;

    private final ChatClient chatClient;

    private final Advisor faqRagAdvisor;

    private final Advisor candidateRagAdvisor;

    public LoveRagApp(
            @Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel,
            @Qualifier("loveAppLocalFaqRagAdvisor") Advisor faqRagAdvisor,
            @Qualifier("loveAppLocalCandidateRagAdvisor") Advisor candidateRagAdvisor) {
        // 挂载日志 Advisor（order=0）观察调用链：RAG Advisor 的 order 为 -100，
        // 会先完成检索增强，因此日志里打印的是拼接了知识库资料后的最终 Prompt。
        this.chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultAdvisors(new StudyLoggerAdvisor())
                .build();
        this.faqRagAdvisor = faqRagAdvisor;
        this.candidateRagAdvisor = candidateRagAdvisor;
    }

    /** 使用本地 FAQ 向量库回答问题，可按用户关系状态过滤资料。 */
    public String chat(String message, String status) {
        return chatClient.prompt()
                .system(FAQ_SYSTEM_PROMPT)
                .user(message)
                // FILTER_EXPRESSION 会进入 RAG Query 的 context，覆盖本次请求的默认 FAQ 过滤条件。
                .advisors(spec -> spec
                        .advisors(faqRagAdvisor)
                        .param(VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                                LoveRagFilterFactory.faq(status)))
                .call()
                .content();
    }

    /**
     * 从候选人向量资料中检索相关对象，并通过 Spring AI 结构化输出映射为 Java record。
     */
    public LoveMatchRecommendation recommend(String message) {
        return chatClient.prompt()
                .system(MATCH_SYSTEM_PROMPT)
                .user(message)
                .advisors(spec -> spec.advisors(candidateRagAdvisor))
                .call()
                .entity(LoveMatchRecommendation.class);
    }

    /** 推荐结果的 Java 类型，便于接口层稳定返回 JSON。 */
    public record LoveMatchRecommendation(String summary, List<MatchCandidate> matches) {
    }

    public record MatchCandidate(String name, Integer matchScore, String matchReason, String firstMessage) {
    }
}
