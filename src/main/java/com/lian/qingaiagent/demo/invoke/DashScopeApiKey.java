package com.lian.qingaiagent.demo.invoke;

/**
 * 统一读取 DashScope 密钥，避免示例代码把真实密钥写入源码。
 */
public final class DashScopeApiKey {

    private DashScopeApiKey() {
    }

    public static String getRequired() {
        // 兼容 DashScope SDK 和 Spring AI Alibaba 文档中常见的两种环境变量名称。
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("AI_DASHSCOPE_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "请先设置 DASHSCOPE_API_KEY 或 AI_DASHSCOPE_API_KEY 环境变量");
        }
        return apiKey;
    }
}
