package com.lian.qingaiagent.mcp.imagesearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSearchToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsPexelsResponseToSmallMcpPayload() throws Exception {
        AtomicReference<String> requestQuery = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/search", exchange -> {
            requestQuery.set(exchange.getRequestURI().getQuery());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, """
                    {
                      "total_results": 42,
                      "photos": [
                        {
                          "id": 7,
                          "url": "https://www.pexels.com/photo/7",
                          "alt": "Shanghai night",
                          "photographer": "Study Photographer",
                          "photographer_url": "https://www.pexels.com/@study",
                          "src": {"medium": "https://images.pexels.com/photos/7/medium.jpeg"}
                        }
                      ]
                    }
                    """);
        });
        server.start();

        try {
            ImageSearchProperties properties = new ImageSearchProperties();
            properties.setApiKey("test-pexels-key");
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());

            ImageSearchTool tool = new ImageSearchTool(properties, RestClient.builder(), objectMapper);
            JsonNode result = objectMapper.readTree(tool.searchImages("上海 夜景", 1));

            assertEquals("上海 夜景", result.path("query").asText());
            assertEquals(42, result.path("totalResults").asLong());
            assertEquals("https://images.pexels.com/photos/7/medium.jpeg",
                    result.path("results").get(0).path("mediumUrl").asText());
            assertTrue(requestQuery.get().contains("per_page=1"));
            assertTrue(requestQuery.get().contains("locale=zh-CN"));
            assertEquals("test-pexels-key", authorization.get());
        }
        finally {
            server.stop(0);
        }
    }

    @Test
    void returnsHelpfulMessageWhenApiKeyIsMissing() throws Exception {
        ImageSearchProperties properties = new ImageSearchProperties();
        ImageSearchTool tool = new ImageSearchTool(properties, RestClient.builder(), objectMapper);

        JsonNode result = objectMapper.readTree(tool.searchImages("computer", 1));

        assertTrue(result.path("error").asText().contains("PEXELS_API_KEY"));
    }

    @Test
    void exposesSpringAiToolAsCallback() {
        ImageSearchProperties properties = new ImageSearchProperties();
        ImageSearchTool tool = new ImageSearchTool(properties, RestClient.builder(), objectMapper);

        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(tool)
                .build()
                .getToolCallbacks();

        assertEquals(1, callbacks.length);
        assertEquals("searchImages", callbacks[0].getToolDefinition().name());
    }

    private static void respond(HttpExchange exchange, String response) throws IOException {
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }
}
