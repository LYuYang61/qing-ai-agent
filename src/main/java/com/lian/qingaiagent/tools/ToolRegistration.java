package com.lian.qingaiagent.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.Assert;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 集中注册第六期工具。
 *
 * <p>工具对象保持无状态或显式接收配置，再由 {@link ToolCallbacks#from(Object...)} 统一转换为
 * Spring AI 的 {@link ToolCallback}。LoveApp 只依赖回调数组，不需要知道每个工具的具体实现。</p>
 */
@Configuration
@Profile("dashscope")
@EnableConfigurationProperties(ToolProperties.class)
public class ToolRegistration {

    /**
     * 注册工具文件存储，供工具使用。
     *
     * @param properties 工具配置
     * @return 工具文件存储
     */
    @Bean
    public ToolFileStorage toolFileStorage(ToolProperties properties) {
        Assert.hasText(properties.getWorkspace(), "qing.ai.tools.workspace 不能为空");
        Assert.isTrue(properties.getMaxFileBytes() > 0, "qing.ai.tools.max-file-bytes 必须大于 0");
        return new ToolFileStorage(Path.of(properties.getWorkspace()), properties.getMaxFileBytes());
    }

    /**
     * 注册工具回调数组，供 Spring AI 统一管理。
     *
     * @param properties 工具配置
     * @param storage    文件存储
     * @return 工具回调数组
     */
    @Bean("loveToolCallbacks")
    public ToolCallback[] loveToolCallbacks(ToolProperties properties, ToolFileStorage storage) {
        List<Object> tools = new ArrayList<>();
        tools.add(new FileOperationTool(storage));
        tools.add(new WebSearchTool(properties.getSearch(), properties.getNetworkTimeoutMs()));
        tools.add(new WebScrapingTool(properties.getNetworkTimeoutMs(), properties.getMaxWebResponseChars()));
        tools.add(new ResourceDownloadTool(storage, properties.getNetworkTimeoutMs(), properties.getMaxDownloadBytes()));
        tools.add(new PdfGenerationTool(storage));
        // 自定义工具：不依赖第三方服务，启动后即可通过模型查询当前时间。
        tools.add(new CurrentTimeTool(properties.getTime().getDefaultZone()));
        if (properties.getTerminal().isEnabled()) {
            // 终端工具具备执行系统命令的能力，只有显式打开开关后才注册给模型。
            tools.add(new TerminalOperationTool(properties.getTerminal().getTimeoutMs()));
        }
        return ToolCallbacks.from(tools.toArray());
    }
}
