package com.lian.qingaiagent.rag;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * 百炼云知识库的 RAG 检索配置。
 *
 * <p>云知识库的文档解析、索引和检索由百炼托管，应用侧只需要通过索引名称创建
 * {@link DashScopeDocumentRetriever}。该 Bean 默认关闭，避免因为用户尚未创建索引而影响项目启动。</p>
 */
@Slf4j
@Configuration
@Profile("dashscope")
@ConditionalOnProperty(prefix = "qing.ai.rag.cloud", name = "enabled", havingValue = "true")
public class LoveAppCloudRagConfiguration {

    /**
     * 与本地 RAG 保持一致：先于 StudyLoggerAdvisor(0) 执行，
     * 日志中才能看到云检索结果拼接进用户消息后的最终 Prompt。
     */
    private static final int RAG_ADVISOR_ORDER = -100;

    @Bean("loveAppCloudRagAdvisor")
    public Advisor loveAppCloudRagAdvisor(
            RagProperties properties,
            @Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        RagProperties.Cloud cloud = properties.getCloud();
        Assert.hasText(apiKey, "启用云知识库前必须配置 spring.ai.dashscope.api-key");
        Assert.hasText(cloud.getIndexName(), "启用云知识库前必须配置 qing.ai.rag.cloud.index-name");

        DashScopeApi.Builder apiBuilder = DashScopeApi.builder().apiKey(apiKey);
        if (StringUtils.hasText(cloud.getWorkspaceId())) {
            apiBuilder.workSpaceId(cloud.getWorkspaceId());
        }
        DashScopeApi dashScopeApi = apiBuilder.build();

        DashScopeDocumentRetrieverOptions retrieverOptions = DashScopeDocumentRetrieverOptions.builder()
                .indexName(cloud.getIndexName())
                .denseSimilarityTopK(cloud.getDenseSimilarityTopK())
                .sparseSimilarityTopK(cloud.getSparseSimilarityTopK())
                // 第四期先观察基础检索链路；查询改写和重排属于后续 RAG 调优内容。
                .enableRewrite(false)
                .enableReranking(false)
                .build();
        DocumentRetriever documentRetriever = new DashScopeDocumentRetriever(dashScopeApi, retrieverOptions);
        log.info("云知识库 RAG Advisor 已创建：索引={}，denseTopK={}，sparseTopK={}",
                cloud.getIndexName(), cloud.getDenseSimilarityTopK(), cloud.getSparseSimilarityTopK());
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .order(RAG_ADVISOR_ORDER)
                .build();
    }
}
