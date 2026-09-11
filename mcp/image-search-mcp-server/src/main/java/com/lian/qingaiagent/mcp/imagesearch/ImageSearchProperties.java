package com.lian.qingaiagent.mcp.imagesearch;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 图片搜索服务的外部配置；API Key 只应通过环境变量注入。 */
@ConfigurationProperties(prefix = "mcp.image-search")
public class ImageSearchProperties {

    private String apiKey = "";

    private String baseUrl = "https://api.pexels.com";

    private String locale = "zh-CN";

    private int defaultLimit = 5;

    private int maxLimit = 10;

    private Duration requestTimeout = Duration.ofSeconds(10);

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(int defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public int getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(int maxLimit) {
        this.maxLimit = maxLimit;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}
