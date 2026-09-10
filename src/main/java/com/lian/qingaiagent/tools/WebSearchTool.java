package com.lian.qingaiagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 SearchAPI 的联网搜索工具。
 *
 * <p>API Key 不参与 Java 源码和提交配置，只有在运行配置中提供后才会真正请求远端服务。
 * 返回值只保留标题、链接和摘要，避免把搜索服务的完整 JSON 无差别塞给模型。</p>
 */
public class WebSearchTool {

    private final String apiKey;

    private final String apiUrl;

    private final String engine;

    private final int resultLimit;

    private final int timeoutMs;

    public WebSearchTool(ToolProperties.Search properties, int timeoutMs) {
        this(properties.getApiKey(), properties.getApiUrl(), properties.getEngine(),
                properties.getResultLimit(), timeoutMs);
    }

    WebSearchTool(String apiKey, String apiUrl, String engine, int resultLimit, int timeoutMs) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.engine = engine;
        this.resultLimit = resultLimit;
        this.timeoutMs = timeoutMs;
    }

    @Tool(name = "searchWeb", description = "使用搜索引擎查询互联网信息，适合获取最新网页、地点或资料线索")
    public String searchWeb(@ToolParam(description = "要搜索的关键词或完整问题") String query) {
        if (!StringUtils.hasText(apiKey)) {
            return "联网搜索不可用：尚未配置 SEARCH_API_KEY。请在运行配置的环境变量中提供 SearchAPI 密钥。";
        }
        if (!StringUtils.hasText(query)) {
            return "联网搜索失败：搜索关键词不能为空";
        }
        if (!StringUtils.hasText(apiUrl) || resultLimit <= 0 || timeoutMs <= 0) {
            return "联网搜索失败：搜索工具配置无效";
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("q", query);
        parameters.put("api_key", apiKey);
        parameters.put("engine", engine);
        try {
            String response = HttpUtil.get(apiUrl, parameters, timeoutMs);
            return formatResults(JSONUtil.parseObj(response));
        }
        catch (Exception exception) {
            return "联网搜索失败：" + exception.getMessage();
        }
    }

    private String formatResults(JSONObject response) {
        JSONArray organicResults = response.getJSONArray("organic_results");
        if (organicResults == null || organicResults.isEmpty()) {
            return "没有找到相关搜索结果。";
        }

        List<String> results = new ArrayList<>();
        int limit = Math.min(resultLimit, organicResults.size());
        for (int i = 0; i < limit; i++) {
            JSONObject result = organicResults.getJSONObject(i);
            results.add("标题：" + result.getStr("title", "")
                    + "\n链接：" + result.getStr("link", "")
                    + "\n摘要：" + result.getStr("snippet", ""));
        }
        return String.join("\n\n", results);
    }
}
