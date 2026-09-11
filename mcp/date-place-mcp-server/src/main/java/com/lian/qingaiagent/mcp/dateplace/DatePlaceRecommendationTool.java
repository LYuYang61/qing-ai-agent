package com.lian.qingaiagent.mcp.dateplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 自定义 MCP 工具：根据地点、偏好和预算推荐约会场所。
 *
 * <p>它故意采用确定性的本地目录筛选，而不是把模型当作数据库。这样 MCP 客户端可以
 * 清楚地区分“模型负责理解意图”和“服务负责返回可验证数据”。</p>
 */
@Service
public class DatePlaceRecommendationTool {

    private static final Logger log = LoggerFactory.getLogger(DatePlaceRecommendationTool.class);

    private final DatePlaceCatalog catalog;

    private final DatePlaceProperties properties;

    private final ObjectMapper objectMapper;

    public DatePlaceRecommendationTool(DatePlaceCatalog catalog,
                                       DatePlaceProperties properties,
                                       ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 推荐约会地点。
     *
     * <p>目录是学习数据，不承诺实时营业、交通距离或空位；工具描述明确这一点，避免
     * 模型把静态目录结果误说成实时地图查询。</p>
     */
    @Tool(name = "recommendDatePlaces",
            description = "根据城市或区域、约会偏好和人均预算，从本地维护的约会地点目录中推荐场所；"
                    + "结果是静态学习数据，不代表实时营业状态、精确距离或空位，请向用户说明这一点")
    public String recommendDatePlaces(
            @ToolParam(description = "城市或区域，例如上海静安区、杭州西湖") String location,
            @ToolParam(required = false, description = "偏好关键词，例如安静、散步、室内、拍照或低预算") String preference,
            @ToolParam(required = false, description = "可接受的人均预算，单位为人民币元") Integer budgetPerPerson,
            @ToolParam(required = false, description = "最多返回数量，默认 5，最大不超过服务配置") Integer limit) {
        try {
            return objectMapper.writeValueAsString(recommend(location, preference, budgetPerPerson, limit));
        }
        catch (Exception exception) {
            log.warn("Date place recommendation failed: {}", exception.getMessage());
            return errorJson("约会地点推荐失败：" + safeMessage(exception));
        }
    }

    private RecommendationResponse recommend(String location,
                                             String preference,
                                             Integer budgetPerPerson,
                                             Integer requestedLimit) {
        if (!StringUtils.hasText(location)) {
            throw new IllegalArgumentException("城市或区域不能为空");
        }
        if (budgetPerPerson != null && budgetPerPerson < 0) {
            throw new IllegalArgumentException("人均预算不能小于 0");
        }

        int limit = boundedLimit(requestedLimit);
        String normalizedLocation = compact(location);
        List<String> preferenceTokens = tokens(preference);
        List<ScoredPlace> scoredPlaces = new ArrayList<>();

        for (DatePlace place : catalog.all()) {
            int locationScore = locationScore(place, normalizedLocation);
            if (locationScore == 0) {
                continue;
            }
            if (budgetPerPerson != null && place.budgetPerPerson() > budgetPerPerson) {
                continue;
            }
            int preferenceScore = preferenceScore(place, preferenceTokens);
            scoredPlaces.add(new ScoredPlace(place, locationScore + preferenceScore));
        }

        List<PlaceRecommendation> recommendations = scoredPlaces.stream()
                .sorted(Comparator.comparingInt(ScoredPlace::score).reversed()
                        .thenComparing(ScoredPlace::rating, Comparator.reverseOrder()))
                .limit(limit)
                .map(scored -> toRecommendation(scored.place(), preferenceTokens))
                .toList();

        String note = recommendations.isEmpty()
                ? "目录中没有同时满足地点和预算条件的记录；可以扩大区域或提高预算后重试。"
                : "这是本地静态目录的筛选结果，不包含实时距离、营业状态和预约信息。"
                + "正式使用前请核验场所官方信息。";
        return new RecommendationResponse(location.trim(), preference, budgetPerPerson, recommendations, note);
    }

    private int locationScore(DatePlace place, String requestedLocation) {
        String city = compact(place.city());
        String district = compact(place.district());
        String placeText = compact(place.city() + place.district() + place.name() + place.address());

        if (placeText.contains(requestedLocation)) {
            return 100;
        }
        int score = 0;
        if (requestedLocation.contains(city) || city.contains(requestedLocation)) {
            score += 35;
        }
        if (requestedLocation.contains(district) || district.contains(requestedLocation)) {
            score += 50;
        }
        return score;
    }

    private int preferenceScore(DatePlace place, List<String> preferenceTokens) {
        if (preferenceTokens.isEmpty()) {
            return 0;
        }
        String placeText = compact(place.category() + String.join("", place.tags()) + place.description());
        int score = 0;
        for (String token : preferenceTokens) {
            if (placeText.contains(token)) {
                score += 10;
            }
        }
        return score;
    }

    private PlaceRecommendation toRecommendation(DatePlace place, List<String> preferenceTokens) {
        String reason = preferenceTokens.isEmpty()
                ? "符合所选城市或区域"
                : "符合地点范围，并命中 " + String.join("、", matchedPreferences(place, preferenceTokens));
        return new PlaceRecommendation(place.name(), place.city(), place.district(), place.category(),
                place.tags(), place.budgetPerPerson(), place.rating(), place.address(), place.description(),
                place.bestTime(), reason);
    }

    private List<String> matchedPreferences(DatePlace place, List<String> preferenceTokens) {
        String placeText = compact(place.category() + String.join("", place.tags()) + place.description());
        return preferenceTokens.stream().filter(placeText::contains).toList();
    }

    private int boundedLimit(Integer requestedLimit) {
        int configuredMax = Math.max(1, properties.getMaxResults());
        int limit = requestedLimit == null ? configuredMax : requestedLimit;
        if (limit < 1) {
            throw new IllegalArgumentException("limit 必须大于 0");
        }
        return Math.min(limit, configuredMax);
    }

    private List<String> tokens(String preference) {
        if (!StringUtils.hasText(preference)) {
            return List.of();
        }
        return List.of(preference.trim().split("[,，、/\\s]+"))
                .stream()
                .map(this::compact)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String compact(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s,，、。.!！?？/]+", "")
                .replace("市", "")
                .replace("区", "")
                .replace("县", "")
                .replace("省", "");
    }

    private String errorJson(String message) {
        try {
            return objectMapper.writeValueAsString(new ErrorPayload(message));
        }
        catch (Exception ignored) {
            return "{\"error\":\"约会地点推荐失败\"}";
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private record ScoredPlace(DatePlace place, int score) {
        private double rating() {
            return place.rating();
        }
    }

    public record RecommendationResponse(String location,
                                         String preference,
                                         Integer budgetPerPerson,
                                         List<PlaceRecommendation> recommendations,
                                         String note) {
    }

    public record PlaceRecommendation(String name,
                                      String city,
                                      String district,
                                      String category,
                                      List<String> tags,
                                      int budgetPerPerson,
                                      double rating,
                                      String address,
                                      String description,
                                      String bestTime,
                                      String reason) {
    }

    private record ErrorPayload(String error) {
    }
}
