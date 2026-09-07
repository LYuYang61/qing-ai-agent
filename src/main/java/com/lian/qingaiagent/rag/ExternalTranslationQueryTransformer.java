package com.lian.qingaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 使用外部翻译 HTTP API 替代 Spring AI 内置的模型翻译转换器。
 *
 * <p>这里采用 LibreTranslate 兼容的 {@code /translate} 接口格式。实现只负责查询转换，
 * 不改变 Query 的历史消息和上下文；外部服务不可用时回退原查询，避免把可选优化变成主链路故障。</p>
 */
@Slf4j
public class ExternalTranslationQueryTransformer implements QueryTransformer {

    private final RestClient restClient;

    private final String sourceLanguage;

    private final String targetLanguage;

    private final String apiKey;

    public ExternalTranslationQueryTransformer(
            RestClient restClient,
            String sourceLanguage,
            String targetLanguage,
            String apiKey) {
        this.restClient = restClient;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.apiKey = apiKey;
    }

    @Override
    public Query transform(Query query) {
        if (query == null || !StringUtils.hasText(query.text())) {
            return query;
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("q", query.text());
        request.put("source", sourceLanguage);
        request.put("target", targetLanguage);
        request.put("format", "text");
        if (StringUtils.hasText(apiKey)) {
            request.put("api_key", apiKey);
        }

        try {
            TranslationResponse response = restClient.post()
                    .uri("/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(TranslationResponse.class);
            if (response == null || !StringUtils.hasText(response.translatedText())) {
                log.warn("外部翻译服务返回空结果，继续使用原查询");
                return query;
            }
            return new Query(response.translatedText(), query.history(), query.context());
        } catch (RestClientException exception) {
            log.warn("外部翻译服务调用失败，继续使用原查询：{}", exception.getMessage());
            return query;
        }
    }

    private record TranslationResponse(String translatedText) {
    }
}
