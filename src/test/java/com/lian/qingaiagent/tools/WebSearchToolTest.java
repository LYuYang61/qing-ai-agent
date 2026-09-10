package com.lian.qingaiagent.tools;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSearchToolTest {

    @Test
    void extractsUsefulFieldsFromSearchResponse() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search", exchange -> {
            String response = """
                    {"organic_results":[
                      {"title":"编程导航","link":"https://codefather.cn","snippet":"学习交流社区"},
                      {"title":"第二条","link":"https://example.com","snippet":"摘要"}
                    ]}
                    """;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/search";
            WebSearchTool tool = new WebSearchTool("test-key", url, "baidu", 5, 3000);

            String result = tool.searchWeb("程序员鱼皮");

            assertTrue(result.contains("编程导航"));
            assertTrue(result.contains("https://codefather.cn"));
            assertTrue(result.contains("学习交流社区"));
        }
        finally {
            server.stop(0);
        }
    }

    @Test
    void explainsMissingApiKey() {
        WebSearchTool tool = new WebSearchTool("", "https://example.com/search", "baidu", 5, 3000);

        assertTrue(tool.searchWeb("恋爱建议").contains("SEARCH_API_KEY"));
    }
}
