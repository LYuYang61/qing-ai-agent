package com.lian.qingaiagent.tools;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceDownloadToolTest {

    @TempDir
    Path tempDir;

    @Test
    void downloadsIntoDownloadNamespace() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/resource.txt", exchange -> {
            byte[] bytes = "下载内容".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/resource.txt";
            ResourceDownloadTool tool = new ResourceDownloadTool(new ToolFileStorage(tempDir, 1024), 3000, 1024);

            String result = tool.downloadResource(url, "resource.txt");

            assertTrue(result.startsWith("资源下载成功："));
            assertEquals("下载内容", Files.readString(tempDir.resolve("download/resource.txt")));
        }
        finally {
            server.stop(0);
        }
    }
}
