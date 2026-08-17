package com.lian.qingaiagent.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 第四期 RAG 示例的开关和检索参数。
 *
 * <p>本地知识库默认只在 {@code dashscope} Profile 中开启；云知识库需要额外配置索引名称，
 * 因此默认关闭，避免项目启动时误调用用户尚未创建的百炼知识库。</p>
 */
@ConfigurationProperties(prefix = "qing.ai.rag")
public class RagProperties {

    private final Local local = new Local();

    private final Cloud cloud = new Cloud();

    public Local getLocal() {
        return local;
    }

    public Cloud getCloud() {
        return cloud;
    }

    public static class Local {

        private boolean enabled;

        private double similarityThreshold = 0.45;

        private int topK = 4;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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
    }

    public static class Cloud {

        private boolean enabled;

        private String indexName;

        private String workspaceId;

        private int denseSimilarityTopK = 5;

        private int sparseSimilarityTopK = 5;

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
    }
}
