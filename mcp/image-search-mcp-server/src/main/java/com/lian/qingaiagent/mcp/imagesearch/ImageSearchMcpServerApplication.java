package com.lian.qingaiagent.mcp.imagesearch;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 图片搜索 MCP 服务端。
 *
 * <p>同一个可执行 JAR 通过 {@code sse} Profile 作为 HTTP/SSE 服务运行，或通过
 * {@code stdio} Profile 作为 MCP 客户端启动的子进程运行。</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(ImageSearchProperties.class)
public class ImageSearchMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageSearchMcpServerApplication.class, args);
    }

    /**
     * 将 Spring AI 的 {@code @Tool} 方法适配为 MCP 服务端可识别的工具集合。
     *
     * <p>这里使用的是 Spring AI 的 ToolCallback，不是 LangChain4j 的工具接口；MCP
     * 服务端 starter 会把该 provider 转换为 MCP 的工具定义和调用处理器。</p>
     */
    @Bean
    public ToolCallbackProvider imageSearchTools(ImageSearchTool imageSearchTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(imageSearchTool)
                .build();
    }
}
