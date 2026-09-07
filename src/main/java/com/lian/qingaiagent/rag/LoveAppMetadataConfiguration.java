package com.lian.qingaiagent.rag;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 本地文档的 AI 元数据增强配置。
 *
 * <p>关键词抽取会为每个文档额外发起 ChatModel 请求，因此默认关闭；打开后可观察
 * {@code excerpt_keywords} 元数据如何参与后续检索和混合索引。</p>
 */
@Configuration
@Profile("dashscope")
@ConditionalOnProperty(
        prefix = "qing.ai.rag.local.metadata",
        name = "keyword-enrichment-enabled",
        havingValue = "true")
public class LoveAppMetadataConfiguration {

    @Bean
    public KeywordMetadataEnricher loveKeywordMetadataEnricher(
            @Qualifier("dashScopeChatModel") ChatModel chatModel,
            RagProperties properties) {
        return new KeywordMetadataEnricher(
                chatModel,
                properties.getLocal().getMetadata().getKeywordCount());
    }
}
