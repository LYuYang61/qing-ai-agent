package com.lian.qingaiagent.tools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.URI;

/** 网页抓取工具：抓取网页正文文本并限制返回长度。 */
public class WebScrapingTool {

    private final int timeoutMs;

    private final int maxResponseChars;

    public WebScrapingTool(int timeoutMs, int maxResponseChars) {
        this.timeoutMs = timeoutMs;
        this.maxResponseChars = maxResponseChars;
    }

    @Tool(name = "scrapeWebPage", description = "抓取指定网页的标题和正文文本，适合分析网页上的公开内容")
    public String scrapeWebPage(@ToolParam(description = "要抓取的 HTTP/HTTPS 网页 URL") String url) {
        try {
            URI uri = ToolUrlValidator.requireHttpUrl(url);
            Document document = Jsoup.connect(uri.toString())
                    .userAgent("qing-ai-agent-study/1.0")
                    .timeout(timeoutMs)
                    .followRedirects(true)
                    .get();
            String title = document.title();
            String body = document.body() == null ? document.text() : document.body().text();
            String result = "标题：" + title + "\n正文：" + body;
            return truncate(result, maxResponseChars);
        }
        catch (Exception exception) {
            return "网页抓取失败：" + exception.getMessage();
        }
    }

    private String truncate(String content, int maxChars) {
        if (maxChars <= 0 || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "\n[网页内容已截断]";
    }
}
