package com.lian.qingaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

/**
 * 无上文对话时直接放行的守卫转换器，专为查询压缩设计。
 *
 * <p>Spring AI 1.1.2 中 RetrievalAugmentationAdvisor 构造的 Query.history 取自完整的
 * prompt instructions（包含 System 消息和本轮用户问题），所以"history 为空"几乎不会发生，
 * 不能作为"没有可压缩上文"的判据。这里改用"历史中出现过 AssistantMessage"判定存在
 * 真实的上一轮问答。反编译确认内置 CompressionQueryTransformer 会无条件调用模型，
 * 首轮对话或无记忆链路上压缩无事可做却照样产生一次模型请求，守卫在此直接短路。</p>
 */
@Slf4j
public class SkipWithoutConversationQueryTransformer implements QueryTransformer {

    private final QueryTransformer delegate;

    public SkipWithoutConversationQueryTransformer(QueryTransformer delegate) {
        this.delegate = delegate;
    }

    @Override
    public Query transform(Query query) {
        boolean hasPriorConversation = query.history() != null
                && query.history().stream().anyMatch(AssistantMessage.class::isInstance);
        if (!hasPriorConversation) {
            log.info("历史中没有模型回复，属于首轮或无记忆链路，跳过查询压缩，保持原查询：{}", query.text());
            return query;
        }
        return delegate.transform(query);
    }
}
