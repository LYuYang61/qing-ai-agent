package com.lian.qingaiagent.tools;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebScrapingToolTest {

    @Test
    void extractsTextAndAppliesResponseLimit() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/page", exchange -> {
            byte[] bytes = "<html><head><title>恋爱案例</title></head><body><p>沟通比争论更重要。</p></body></html>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/page";
            String result = new WebScrapingTool(3000, 100).scrapeWebPage(url);

            assertTrue(result.contains("恋爱案例"));
            assertTrue(result.contains("沟通比争论更重要"));
        }
        finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsNonHttpUrl() {
        String result = new WebScrapingTool(3000, 100).scrapeWebPage("file:///etc/passwd");

        assertTrue(result.startsWith("网页抓取失败："));
    }
}
