package com.lian.qingaiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 查询增强装饰器与守卫的行为验证：透传语义、首轮跳过压缩、带历史正常委托。
 */
class QueryEnhancementDecoratorsTest {

    @Test
    void loggingTransformerDelegatesAndReturnsTransformedQuery() {
        Query input = new Query("那怎么办？");
        Query output = new Query("相亲被拒后如何调整心态？");
        QueryTransformer delegate = mock(QueryTransformer.class);
        when(delegate.transform(input)).thenReturn(output);

        Query result = new LoggingQueryTransformer("查询压缩", delegate).transform(input);

        assertSame(output, result);
    }

    @Test
    void loggingExpanderDelegatesAndReturnsVariants() {
        Query input = new Query("如何缓解恋爱焦虑");
        List<Query> variants = List.of(
                new Query("恋爱焦虑怎么调节"),
                new Query("如何降低恋爱中的焦虑感"));
        QueryExpander delegate = mock(QueryExpander.class);
        when(delegate.expand(input)).thenReturn(variants);

        List<Query> result = new LoggingQueryExpander("查询扩展", delegate).expand(input);

        assertEquals(variants, result);
    }

    @Test
    void skipsCompressionWhenHistoryContainsNoAssistantMessage() {
        // 首轮请求：历史里只有 System/本轮用户消息的形态，没有任何模型回复。
        Query firstTurn = new Query("单身怎么认识新朋友？",
                List.of(new UserMessage("单身怎么认识新朋友？")), Map.of());
        QueryTransformer compression = mock(QueryTransformer.class);

        Query result = new SkipWithoutConversationQueryTransformer(compression).transform(firstTurn);

        assertSame(firstTurn, result);
        verifyNoInteractions(compression);
    }

    @Test
    void compressesWhenPriorConversationExists() {
        Query followUp = new Query("那线上交友要注意什么？",
                List.of(new UserMessage("单身怎么认识新朋友？"),
                        new AssistantMessage("可以参加兴趣社群、朋友介绍等活动。"),
                        new UserMessage("那线上交友要注意什么？")), Map.of());
        Query compressed = new Query("单身人士线上交友有哪些注意事项？");
        QueryTransformer compression = mock(QueryTransformer.class);
        when(compression.transform(followUp)).thenReturn(compressed);

        Query result = new SkipWithoutConversationQueryTransformer(compression).transform(followUp);

        assertSame(compressed, result);
    }
}
