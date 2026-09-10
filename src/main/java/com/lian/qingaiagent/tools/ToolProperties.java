package com.lian.qingaiagent.tools;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * 第六期工具调用示例的配置。
 *
 * <p>工具类本身保持为普通 Java 类，便于单元测试；Spring 只负责在注册阶段把外部配置组装进去。
 * 所有会访问网络、文件系统或进程的能力都集中在这里，后续替换工具实现时不需要把配置散落到业务代码中。</p>
 */
@ConfigurationProperties(prefix = "qing.ai.tools")
public class ToolProperties {

    /** 工具文件的隔离根目录，默认位于项目临时目录下。 */
    private String workspace = Path.of(System.getProperty("user.dir"), "tmp", "tools").toString();

    /** 文件读写工具允许处理的最大 UTF-8 字节数。 */
    private long maxFileBytes = 2 * 1024 * 1024L;

    /** 网页抓取和搜索的连接、读取超时时间。 */
    private int networkTimeoutMs = 10_000;

    /** 网页抓取返回给模型的最大字符数，避免把整页内容直接塞进上下文。 */
    private int maxWebResponseChars = 12_000;

    /** 资源下载的最大字节数。 */
    private long maxDownloadBytes = 10 * 1024 * 1024L;

    private final Search search = new Search();

    private final Terminal terminal = new Terminal();

    private final Time time = new Time();

    public String getWorkspace() {
        return workspace;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public int getNetworkTimeoutMs() {
        return networkTimeoutMs;
    }

    public void setNetworkTimeoutMs(int networkTimeoutMs) {
        this.networkTimeoutMs = networkTimeoutMs;
    }

    public int getMaxWebResponseChars() {
        return maxWebResponseChars;
    }

    public void setMaxWebResponseChars(int maxWebResponseChars) {
        this.maxWebResponseChars = maxWebResponseChars;
    }

    public long getMaxDownloadBytes() {
        return maxDownloadBytes;
    }

    public void setMaxDownloadBytes(long maxDownloadBytes) {
        this.maxDownloadBytes = maxDownloadBytes;
    }

    public Search getSearch() {
        return search;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public Time getTime() {
        return time;
    }

    public static class Search {

        /** SearchAPI 的密钥只从环境变量或 IDEA 运行配置注入。 */
        private String apiKey;

        private String apiUrl = "https://www.searchapi.io/api/v1/search";

        private String engine = "baidu";

        private int resultLimit = 5;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getEngine() {
            return engine;
        }

        public void setEngine(String engine) {
            this.engine = engine;
        }

        public int getResultLimit() {
            return resultLimit;
        }

        public void setResultLimit(int resultLimit) {
            this.resultLimit = resultLimit;
        }
    }

    public static class Terminal {

        /** 终端工具默认关闭，必须由使用者明确打开。 */
        private boolean enabled;

        private int timeoutMs = 10_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class Time {

        private String defaultZone = "Asia/Shanghai";

        public String getDefaultZone() {
            return defaultZone;
        }

        public void setDefaultZone(String defaultZone) {
            this.defaultZone = defaultZone;
        }
    }
}
