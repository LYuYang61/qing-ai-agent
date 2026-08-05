package com.lian.qingaiagent.demo.invoke;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * 直接使用 Java HTTP 客户端调用 DashScope REST API。
 */
public final class HttpAiInvoke {

    private static final String URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

    private HttpAiInvoke() {
    }

    public static String call(String question) throws IOException, InterruptedException {
        // DashScope 的对话接口要求将模型、消息和返回格式组装到 JSON 请求体中。
        String requestBody = """
                {
                  "model": "qwen3.7-max",
                  "input": {
                    "messages": [
                      {"role": "system", "content": "You are a helpful assistant."},
                      {"role": "user", "content": "%s"}
                    ]
                  },
                  "parameters": {"result_format": "message"}
                }
                """.formatted(escapeJson(question));

        // Bearer Token 放在 Authorization 请求头中，API 不需要额外的 SDK。
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .header("Authorization", "Bearer " + DashScopeApiKey.getRequired())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        // 先检查 HTTP 状态，再把响应原文返回，便于观察 DashScope 的完整结果结构。
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("DashScope HTTP 调用失败，状态码: " + response.statusCode()
                    + ", 响应: " + response.body());
        }
        return response.body();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println(call("你是谁？"));
    }
}
