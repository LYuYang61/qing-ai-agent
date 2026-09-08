package com.lian.qingaiagent.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 示例的开关、检索参数和外部服务配置。
 *
 * <p>本地知识库默认只在 {@code dashscope} Profile 中开启；云知识库需要额外配置索引名称，
 * 因此默认关闭，避免项目启动时误调用用户尚未创建的百炼知识库。</p>
 */
@ConfigurationProperties(prefix = "qing.ai.rag")
public class RagProperties {

    private final Local local = new Local();

    private final Cloud cloud = new Cloud();

    private final Hybrid hybrid = new Hybrid();

    private final QueryTranslation queryTranslation = new QueryTranslation();

    private final QueryCompression queryCompression = new QueryCompression();

    private final QueryRewrite queryRewrite = new QueryRewrite();

    private final QueryExpansion queryExpansion = new QueryExpansion();

    private final Postgres postgres = new Postgres();

    public Local getLocal() {
        return local;
    }

    public Cloud getCloud() {
        return cloud;
    }

    public Hybrid getHybrid() {
        return hybrid;
    }

    public QueryTranslation getQueryTranslation() {
        return queryTranslation;
    }

    public QueryCompression getQueryCompression() {
        return queryCompression;
    }

    public QueryRewrite getQueryRewrite() {
        return queryRewrite;
    }

    public QueryExpansion getQueryExpansion() {
        return queryExpansion;
    }

    public Postgres getPostgres() {
        return postgres;
    }

    public static class Local {

        private boolean enabled;

        private String store = "memory";

        private double similarityThreshold = 0.45;

        private int topK = 4;

        private final Metadata metadata = new Metadata();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getStore() {
            return store;
        }

        public void setStore(String store) {
            this.store = store;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public Metadata getMetadata() {
            return metadata;
        }
    }

    public static class Cloud {

        private boolean enabled;

        private String indexName;

        private String workspaceId;

        private int denseSimilarityTopK = 5;

        private int sparseSimilarityTopK = 5;

        /** 百炼控制台负责抽取和保存文档标签；应用侧负责按标签检索。 */
        private final Metadata metadata = new Metadata();

        /** DashScope 检索接口使用的搜索过滤器结构。 */
        private List<Map<String, Object>> searchFilters = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getIndexName() {
            return indexName;
        }

        public void setIndexName(String indexName) {
            this.indexName = indexName;
        }

        public String getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
        }

        public int getDenseSimilarityTopK() {
            return denseSimilarityTopK;
        }

        public void setDenseSimilarityTopK(int denseSimilarityTopK) {
            this.denseSimilarityTopK = denseSimilarityTopK;
        }

        public int getSparseSimilarityTopK() {
            return sparseSimilarityTopK;
        }

        public void setSparseSimilarityTopK(int sparseSimilarityTopK) {
            this.sparseSimilarityTopK = sparseSimilarityTopK;
        }

        public Metadata getMetadata() {
            return metadata;
        }

        public List<Map<String, Object>> getSearchFilters() {
            return searchFilters;
        }

        public void setSearchFilters(List<Map<String, Object>> searchFilters) {
            this.searchFilters = searchFilters == null ? new ArrayList<>() : searchFilters;
        }
    }

    public static class Metadata {

        private boolean keywordEnrichmentEnabled;

        private int keywordCount = 5;

        private boolean autoExtractionEnabled;

        private List<String> fields = new ArrayList<>();

        public boolean isKeywordEnrichmentEnabled() {
            return keywordEnrichmentEnabled;
        }

        public void setKeywordEnrichmentEnabled(boolean keywordEnrichmentEnabled) {
            this.keywordEnrichmentEnabled = keywordEnrichmentEnabled;
        }

        public int getKeywordCount() {
            return keywordCount;
        }

        public void setKeywordCount(int keywordCount) {
            this.keywordCount = keywordCount;
        }

        public boolean isAutoExtractionEnabled() {
            return autoExtractionEnabled;
        }

        public void setAutoExtractionEnabled(boolean autoExtractionEnabled) {
            this.autoExtractionEnabled = autoExtractionEnabled;
        }

        public List<String> getFields() {
            return fields;
        }

        public void setFields(List<String> fields) {
            this.fields = fields == null ? new ArrayList<>() : fields;
        }
    }

    public static class Hybrid {

        private boolean enabled;

        private int keywordTopK = 6;

        private int resultTopK = 6;

        private int reciprocalRankConstant = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getKeywordTopK() {
            return keywordTopK;
        }

        public void setKeywordTopK(int keywordTopK) {
            this.keywordTopK = keywordTopK;
        }

        public int getResultTopK() {
            return resultTopK;
        }

        public void setResultTopK(int resultTopK) {
            this.resultTopK = resultTopK;
        }

        public int getReciprocalRankConstant() {
            return reciprocalRankConstant;
        }

        public void setReciprocalRankConstant(int reciprocalRankConstant) {
            this.reciprocalRankConstant = reciprocalRankConstant;
        }
    }

    public static class QueryTranslation {

        private boolean enabled;

        private String baseUrl = "https://libretranslate.com";

        private String apiKey;

        private String sourceLanguage = "auto";

        private String targetLanguage = "zh";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getSourceLanguage() {
            return sourceLanguage;
        }

        public void setSourceLanguage(String sourceLanguage) {
            this.sourceLanguage = sourceLanguage;
        }

        public String getTargetLanguage() {
            return targetLanguage;
        }

        public void setTargetLanguage(String targetLanguage) {
            this.targetLanguage = targetLanguage;
        }
    }

    /** 查询压缩：把带上下文的追问改写成独立问题，服务于记忆链路上的连续对话检索。 */
    public static class QueryCompression {

        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** 查询重写：面向检索系统规范化查询表述。 */
    public static class QueryRewrite {

        private boolean enabled;

        /** RewriteQueryTransformer 的检索目标描述，与内置默认值一致，保留为可配置项便于实验对比。 */
        private String targetSearchSystem = "vector store";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTargetSearchSystem() {
            return targetSearchSystem;
        }

        public void setTargetSearchSystem(String targetSearchSystem) {
            this.targetSearchSystem = targetSearchSystem;
        }
    }

    /** 多查询扩展：一个问题裂变成多个检索变体。 */
    public static class QueryExpansion {

        private boolean enabled;

        private int numberOfQueries = 3;

        /** 原查询是否与变体一起参与检索；默认开启，变体集体跑偏时保底召回。 */
        private boolean includeOriginal = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getNumberOfQueries() {
            return numberOfQueries;
        }

        public void setNumberOfQueries(int numberOfQueries) {
            this.numberOfQueries = numberOfQueries;
        }

        public boolean isIncludeOriginal() {
            return includeOriginal;
        }

        public void setIncludeOriginal(boolean includeOriginal) {
            this.includeOriginal = includeOriginal;
        }
    }

    public static class Postgres {

        private boolean enabled;

        private String url;

        private String username;

        private String password;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
