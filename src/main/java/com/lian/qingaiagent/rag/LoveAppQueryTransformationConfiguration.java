package com.lian.qingaiagent.rag;

import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** 可选的外部翻译查询转换器配置。 */
@Configuration
@Profile("dashscope")
@ConditionalOnProperty(prefix = "qing.ai.rag.query-translation", name = "enabled", havingValue = "true")
public class LoveAppQueryTransformationConfiguration {

    @Bean
    public QueryTransformer loveExternalTranslationQueryTransformer(RagProperties properties) {
        RagProperties.QueryTranslation translation = properties.getQueryTranslation();
        Assert.isTrue(StringUtils.hasText(translation.getBaseUrl()),
                "启用外部翻译查询转换前必须配置 qing.ai.rag.query-translation.base-url");
        Assert.isTrue(StringUtils.hasText(translation.getSourceLanguage()),
                "必须配置外部翻译源语言");
        Assert.isTrue(StringUtils.hasText(translation.getTargetLanguage()),
                "必须配置外部翻译目标语言");
        RestClient restClient = RestClient.builder()
                .baseUrl(translation.getBaseUrl())
                .build();
        return new ExternalTranslationQueryTransformer(
                restClient,
                translation.getSourceLanguage(),
                translation.getTargetLanguage(),
                translation.getApiKey());
    }
}
