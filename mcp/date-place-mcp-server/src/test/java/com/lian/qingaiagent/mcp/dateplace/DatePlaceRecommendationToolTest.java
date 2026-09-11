package com.lian.qingaiagent.mcp.dateplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatePlaceRecommendationToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void filtersLocalCatalogByLocationPreferenceAndBudget() throws Exception {
        DatePlaceProperties properties = new DatePlaceProperties();
        DatePlaceCatalog catalog = new DatePlaceCatalog(
                properties, new DefaultResourceLoader(), objectMapper);
        DatePlaceRecommendationTool tool = new DatePlaceRecommendationTool(catalog, properties, objectMapper);

        JsonNode result = objectMapper.readTree(
                tool.recommendDatePlaces("上海静安区", "安静 散步", 80, 3));

        assertEquals(2, result.path("recommendations").size());
        assertEquals("静安雕塑公园", result.path("recommendations").get(0).path("name").asText());
        assertTrue(result.path("note").asText().contains("静态目录"));
    }

    @Test
    void returnsEmptyRecommendationsWhenBudgetDoesNotMatch() throws Exception {
        DatePlaceProperties properties = new DatePlaceProperties();
        DatePlaceCatalog catalog = new DatePlaceCatalog(
                properties, new DefaultResourceLoader(), objectMapper);
        DatePlaceRecommendationTool tool = new DatePlaceRecommendationTool(catalog, properties, objectMapper);

        JsonNode result = objectMapper.readTree(
                tool.recommendDatePlaces("上海静安区", "室内", 10, 3));

        assertEquals(0, result.path("recommendations").size());
        assertTrue(result.path("note").asText().contains("没有同时满足"));
    }

    @Test
    void exposesCustomServiceAsSpringAiTool() {
        DatePlaceProperties properties = new DatePlaceProperties();
        DatePlaceCatalog catalog = new DatePlaceCatalog(
                properties, new DefaultResourceLoader(), objectMapper);
        DatePlaceRecommendationTool tool = new DatePlaceRecommendationTool(catalog, properties, objectMapper);

        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(tool)
                .build()
                .getToolCallbacks();

        assertEquals(List.of("recommendDatePlaces"),
                List.of(callbacks[0].getToolDefinition().name()));
    }
}
