package com.lian.qingaiagent.mcp.dateplace;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 自定义约会地点推荐 MCP 服务端。
 *
 * <p>服务不依赖地图 API Key，而是从可替换的本地目录中进行可解释筛选，适合作为
 * MCP 的稳定学习样例；需要实时距离或营业状态时，再把目录实现替换为地图服务。</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(DatePlaceProperties.class)
public class DatePlaceMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatePlaceMcpServerApplication.class, args);
    }

    /** 将自定义工具适配为 Spring AI MCP 服务端能够发布的工具定义。 */
    @Bean
    public ToolCallbackProvider datePlaceTools(DatePlaceRecommendationTool recommendationTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(recommendationTool)
                .build();
    }
}
