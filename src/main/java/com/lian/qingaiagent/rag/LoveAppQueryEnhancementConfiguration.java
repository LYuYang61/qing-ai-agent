package com.lian.qingaiagent.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 查询压缩、重写与多查询扩展的条件 Bean 配置。
 *
 * <p>Bean 名称与 {@link LoveRagQueryChain} 的槽位一一对应：
 * loveCompressionQueryTransformer、loveRewriteQueryTransformer 进入转换器链
 * （压缩 → 重写 → 翻译 的固定顺序），loveMultiQueryExpander 的类型 QueryExpander
 * 全项目唯一，由 Advisor 的扩展器插槽按类型自动装配，无需名称限定。
 * 压缩默认开启（守卫保证首轮与无记忆链路零额外成本），重写与扩展默认关闭；
 * 开启的每一项在每次检索前各多一次模型调用，扩展还会按变体数量放大检索轮次。</p>
 */
@Configuration
@Profile("dashscope")
public class LoveAppQueryEnhancementConfiguration {

    @Bean("loveCompressionQueryTransformer")
    @ConditionalOnProperty(prefix = "qing.ai.rag.query-compression", name = "enabled", havingValue = "true")
    public QueryTransformer loveCompressionQueryTransformer(
            @Qualifier("dashScopeChatModel") ChatModel chatModel) {
        CompressionQueryTransformer compression = CompressionQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(chatModel))
                .build();
        // 日志装饰器在最外层；无上文守卫在内层，首轮或无记忆链路直接短路掉注定多余的模型调用。
        return new LoggingQueryTransformer("查询压缩",
                new SkipWithoutConversationQueryTransformer(compression));
    }

    @Bean("loveRewriteQueryTransformer")
    @ConditionalOnProperty(prefix = "qing.ai.rag.query-rewrite", name = "enabled", havingValue = "true")
    public QueryTransformer loveRewriteQueryTransformer(
            @Qualifier("dashScopeChatModel") ChatModel chatModel,
            RagProperties properties) {
        RewriteQueryTransformer rewrite = RewriteQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(chatModel))
                // 内置默认 targetSearchSystem 就是 "vector store"，保留为配置项便于实验对比。
                .targetSearchSystem(properties.getQueryRewrite().getTargetSearchSystem())
                .build();
        return new LoggingQueryTransformer("查询重写", rewrite);
    }

    @Bean("loveMultiQueryExpander")
    @ConditionalOnProperty(prefix = "qing.ai.rag.query-expansion", name = "enabled", havingValue = "true")
    public QueryExpander loveMultiQueryExpander(
            @Qualifier("dashScopeChatModel") ChatModel chatModel,
            RagProperties properties) {
        RagProperties.QueryExpansion expansion = properties.getQueryExpansion();
        MultiQueryExpander expander = MultiQueryExpander.builder()
                .chatClientBuilder(ChatClient.builder(chatModel))
                .numberOfQueries(expansion.getNumberOfQueries())
                // 原查询与变体一起参与检索：变体集体跑偏时保底召回。
                .includeOriginal(expansion.isIncludeOriginal())
                .build();
        return new LoggingQueryExpander("查询扩展", expander);
    }
}
