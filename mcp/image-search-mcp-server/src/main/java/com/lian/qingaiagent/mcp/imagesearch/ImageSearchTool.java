package com.lian.qingaiagent.mcp.imagesearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 通过 Pexels REST API 搜索图片的 MCP 工具。
 *
 * <p>工具结果保留图片页面、摄影师和图片地址，方便上层 AI 给出可追溯的结果；不把
 * Pexels 的完整响应直接塞进模型上下文，也不在代码中保存 API Key。</p>
 */
@Service
public class ImageSearchTool {

    private static final Logger log = LoggerFactory.getLogger(ImageSearchTool.class);

    private static final String SEARCH_PATH = "/v1/search";

    private final ImageSearchProperties properties;

    private final RestClient restClient;

    private final ObjectMapper objectMapper;

    public ImageSearchTool(ImageSearchProperties properties,
                            RestClient.Builder restClientBuilder,
                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        // 只记录密钥是否存在，绝不打印密钥内容；排查"配置了却没生效"类问题的一眼定案手段。
        log.info("ImageSearchTool 初始化：Pexels API 密钥{}",
                StringUtils.hasText(properties.getApiKey()) ? "已配置" : "未配置（搜索将返回配置缺失提示）");

        // RestClient 默认不会替业务请求设置超时；这里显式限制外部 API 等待时间。
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getRequestTimeout());
        requestFactory.setReadTimeout(properties.getRequestTimeout());
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 搜索网络图片。
     *
     * <p>这是 MCP 暴露给客户端的工具方法。limit 由服务端再次限制，不能完全相信模型传入的
     * 参数，以免一次调用返回过多图片、消耗过多上下文或触发第三方配额。</p>
     */
    @Tool(name = "searchImages",
            description = "从 Pexels 搜索公开图片，返回图片页面地址、缩略图地址和摄影师信息；"
                    + "仅当用户明确要求查找、展示或下载图片时才调用本工具，"
                    + "不要为了美化或丰富其他回答而主动搜索图片；"
                    + "图片链接只能来自本工具的真实返回，更换关键词时必须重新调用，禁止凭记忆给出或编造任何图片地址；"
                    + "结果展示时应保留 Pexels 与摄影师署名")
    public String searchImages(
            @ToolParam(description = "图片搜索关键词，例如 上海 夜景、情侣约会或 coding") String query,
            @ToolParam(required = false, description = "返回数量，范围为 1 到 10，默认 5") Integer limit) {
        try {
            SearchPayload payload = searchPhotos(query, limit);
            return objectMapper.writeValueAsString(payload);
        }
        catch (Exception exception) {
            log.warn("Pexels image search failed: {}", exception.getMessage());
            return errorJson("图片搜索失败：" + safeMessage(exception));
        }
    }

    /**
     * 保留教程中“只取 medium URL”的学习入口，便于直接单元测试 HTTP 调用和 JSON 映射。
     */
    public List<String> searchMediumImages(String query) {
        return searchPhotos(query, null).results().stream()
                .map(ImageSearchResult::mediumUrl)
                .filter(StringUtils::hasText)
                .toList();
    }

    private SearchPayload searchPhotos(String query, Integer requestedLimit) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("搜索关键词不能为空");
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("未配置 PEXELS_API_KEY，请在启动环境变量中设置 Pexels API Key");
        }

        int limit = boundedLimit(requestedLimit);
        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(SEARCH_PATH)
                        .queryParam("query", query.trim())
                        .queryParam("per_page", limit)
                        .queryParam("locale", properties.getLocale())
                        .build())
                .header(HttpHeaders.AUTHORIZATION, properties.getApiKey())
                .retrieve()
                .body(JsonNode.class);

        if (root == null) {
            throw new IllegalStateException("Pexels 返回空响应");
        }

        List<ImageSearchResult> results = new ArrayList<>();
        for (JsonNode photo : root.path("photos")) {
            JsonNode source = photo.path("src");
            results.add(new ImageSearchResult(
                    photo.path("id").asLong(),
                    textOrNull(photo, "url"),
                    textOrNull(source, "medium"),
                    textOrNull(photo, "alt"),
                    textOrNull(photo, "photographer"),
                    textOrNull(photo, "photographer_url")));
        }

        return new SearchPayload(
                query.trim(),
                root.path("total_results").asLong(0),
                results,
                "图片来自 Pexels；展示时请保留 Pexels 链接和摄影师署名");
    }

    private int boundedLimit(Integer requestedLimit) {
        int limit = requestedLimit == null ? properties.getDefaultLimit() : requestedLimit;
        if (limit < 1) {
            throw new IllegalArgumentException("limit 必须大于 0");
        }
        return Math.min(limit, Math.max(1, properties.getMaxLimit()));
    }

    private String errorJson(String message) {
        try {
            return objectMapper.writeValueAsString(new ErrorPayload(message));
        }
        catch (Exception ignored) {
            return "{\"error\":\"图片搜索失败\"}";
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private static String textOrNull(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field == null || field.isNull() ? null : field.asText();
    }

    public record SearchPayload(String query,
                                long totalResults,
                                List<ImageSearchResult> results,
                                String attribution) {
    }

    public record ImageSearchResult(long id,
                                    String pageUrl,
                                    String mediumUrl,
                                    String alt,
                                    String photographer,
                                    String photographerUrl) {
    }

    private record ErrorPayload(String error) {
    }
}
