package com.lian.qingaiagent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.List;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * 本地 PostgreSQL + PGVector 配置。
 *
 * <p>只有显式打开 {@code qing.ai.rag.postgres.enabled=true} 且将本地存储切换为
 * {@code pgvector} 时才创建 PGVector Bean，因此默认内存 RAG 不需要 PostgreSQL。</p>
 */
@Configuration
@Profile("dashscope")
@ConditionalOnProperty(prefix = "qing.ai.rag.postgres", name = "enabled", havingValue = "true")
public class LoveAppPostgresConfiguration {

    /**
     * DashScope 文本 Embedding 的保守批次上限。
     *
     * <p>PgVectorStore 默认的 TokenCountBatchingStrategy 只按 token 数分批，
     * 不保证单批文本条数；这里在调用 VectorStore.add 前显式按 10 条切分，
     * 避免小文本被合并成超过供应商条数限制的请求。</p>
     */
    static final int EMBEDDING_BATCH_SIZE = 10;

    @Bean
    public DataSource loveAppPostgresDataSource(RagProperties properties) {
        RagProperties.Postgres postgres = properties.getPostgres();
        Assert.hasText(postgres.getUrl(), "启用 PostgreSQL 前必须配置 qing.ai.rag.postgres.url");
        Assert.hasText(postgres.getUsername(), "启用 PostgreSQL 前必须配置 qing.ai.rag.postgres.username");
        Assert.hasText(postgres.getPassword(), "启用 PostgreSQL 前必须配置 qing.ai.rag.postgres.password");

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }

    @Bean
    public JdbcTemplate loveAppJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /** 混合检索额外使用同一个 PostgreSQL 数据库存放可全文匹配的文档副本。 */
    @Bean
    @ConditionalOnProperty(prefix = "qing.ai.rag.hybrid", name = "enabled", havingValue = "true")
    public PostgresKeywordDocumentStore loveAppPostgresKeywordDocumentStore(
            JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new PostgresKeywordDocumentStore(jdbcTemplate, objectMapper);
    }

    /**
     * 把本地 Markdown 文档向量化并放入 PGVector 持久化向量库。
     *
     * <p>PGVector 需要 PostgreSQL 15+，且安装 pgvector 扩展。它的 HNSW 索引在重启后仍然可用，
     * 适合生产环境；但在开发环境中，PGVector 的索引建表和向量写入会比内存向量库慢很多。</p>
     */
    @Bean("loveAppVectorStore")
    @ConditionalOnProperty(prefix = "qing.ai.rag.local", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "qing.ai.rag.local", name = "store", havingValue = "pgvector")
    public VectorStore loveAppPgVectorStore(
            JdbcTemplate jdbcTemplate,
            @Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel,
            LoveKnowledgeIngestionService ingestionService,
            ObjectProvider<PostgresKeywordDocumentStore> keywordDocumentStoreProvider) {
        PgVectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                // 不硬编码 dimensions，让当前 EmbeddingModel 提供真实维度，避免更换模型后表结构不匹配。
                .distanceType(COSINE_DISTANCE)
                .indexType(HNSW)
                .initializeSchema(true)
                .schemaName("public")
                .vectorTableName("vector_store")
                // 这里控制 JDBC 写入批次；Embedding 请求的条数由 addDocumentsInBatches 单独控制。
                .maxDocumentBatchSize(EMBEDDING_BATCH_SIZE)
                .build();
        List<Document> chunks = ingestionService.prepareChunks();
        if (chunks.isEmpty()) {
            throw new IllegalStateException("RAG 知识库没有可写入 PGVector 的文档");
        }
        // @Bean 方法内部尚未进入 Spring 的 InitializingBean 生命周期，需要先显式建表；
        // PgVectorStore 的 upsert 会依据稳定 ID 更新重启后的同一批切片。
        vectorStore.afterPropertiesSet();
        addDocumentsInBatches(vectorStore, chunks);
        PostgresKeywordDocumentStore keywordDocumentStore = keywordDocumentStoreProvider.getIfAvailable();
        if (keywordDocumentStore != null) {
            keywordDocumentStore.replaceAll(chunks);
        }
        return vectorStore;
    }

    /**
     * 在进入 PgVectorStore 前建立明确的 Embedding 条数边界。
     *
     * <p>每次 {@code VectorStore.add} 都会触发一次 Embedding 流程；不能只依赖
     * PgVectorStore 的 {@code maxDocumentBatchSize}，因为它发生在向量生成之后。</p>
     */
    static void addDocumentsInBatches(VectorStore vectorStore, List<Document> documents) {
        for (int start = 0; start < documents.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, documents.size());
            // 复制 subList，避免异步或重试场景下引用原始可变列表的视图。
            vectorStore.add(List.copyOf(documents.subList(start, end)));
        }
    }

    /**
     * 构造混合检索使用的 DocumentRetriever，先用向量检索，再用 PostgreSQL 关键词检索。
     *
     * <p>如果没有足够相关的资料，ContextualQueryAugmenter 会阻止模型继续生成回答，而是返回
     * emptyContextPromptTemplate 中的提示语。</p>
     */
    @Bean("loveAppHybridDocumentRetriever")
    @ConditionalOnProperty(prefix = "qing.ai.rag.hybrid", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "qing.ai.rag.local", name = "enabled", havingValue = "true")
    public DocumentRetriever loveAppHybridDocumentRetriever(
            @Qualifier("loveAppLocalFaqDocumentRetriever") DocumentRetriever vectorRetriever,
            PostgresKeywordDocumentStore keywordDocumentStore,
            RagProperties properties) {
        return new HybridDocumentRetriever(
                vectorRetriever,
                keywordDocumentStore,
                properties.getHybrid().getKeywordTopK(),
                properties.getHybrid().getResultTopK(),
                properties.getHybrid().getReciprocalRankConstant());
    }

    /**
     * 构造混合检索使用的 RAG Advisor，先用向量检索，再用 PostgreSQL 关键词检索。
     *
     * <p>如果没有足够相关的资料，ContextualQueryAugmenter 会阻止模型继续生成回答，而是返回
     * emptyContextPromptTemplate 中的提示语。</p>
     */
    @Bean("loveAppHybridRagAdvisor")
    @ConditionalOnProperty(prefix = "qing.ai.rag.hybrid", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "qing.ai.rag.local", name = "enabled", havingValue = "true")
    public org.springframework.ai.chat.client.advisor.api.Advisor loveAppHybridRagAdvisor(
            @Qualifier("loveAppHybridDocumentRetriever") DocumentRetriever documentRetriever,
            @Qualifier("loveCompressionQueryTransformer") ObjectProvider<QueryTransformer> compressionProvider,
            @Qualifier("loveRewriteQueryTransformer") ObjectProvider<QueryTransformer> rewriteProvider,
            @Qualifier("loveExternalTranslationQueryTransformer") ObjectProvider<QueryTransformer> translationProvider,
            ObjectProvider<QueryExpander> queryExpanderProvider) {
        RetrievalAugmentationAdvisor.Builder advisorBuilder = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever);
        // 查询转换器链与扩展器统一走 LoveRagQueryChain，与本地 FAQ/候选人 Advisor 保持一致。
        LoveRagQueryChain.applyToBuilder(
                advisorBuilder,
                LoveRagQueryChain.assembleQueryTransformers(compressionProvider, rewriteProvider, translationProvider),
                queryExpanderProvider);
        return advisorBuilder.order(-100).build();
    }
}
