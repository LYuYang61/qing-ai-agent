package com.lian.qingaiagent.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * 本地 RAG 的 ETL、向量化和检索增强配置。
 *
 * <p>SimpleVectorStore 只把向量保存在当前 JVM 内存中，适合学习完整链路；生产环境应替换为
 * PGVector 等持久化向量数据库。为避免默认启动就消耗 Embedding API，本配置只有在显式开启
 * {@code qing.ai.rag.local.enabled} 时才创建。</p>
 */
@Configuration
@Profile("dashscope")
@ConditionalOnProperty(prefix = "qing.ai.rag.local", name = "enabled", havingValue = "true")
public class LoveAppLocalRagConfiguration {

    private static final int RAG_ADVISOR_ORDER = -100;

    /**
     * 构造恋爱知识库的内存向量库。
     *
     * <p>如果没有可向量化的 Markdown 文档，抛出异常阻止应用启动。</p>
     */
    @Bean("loveAppVectorStore")
    @ConditionalOnProperty(
            prefix = "qing.ai.rag.local", name = "store", havingValue = "memory", matchIfMissing = true)
    public SimpleVectorStore loveAppVectorStore(
            @Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel,
            LoveKnowledgeIngestionService ingestionService,
            ObjectProvider<PostgresKeywordDocumentStore> keywordDocumentStoreProvider) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        List<Document> chunks = ingestionService.prepareChunks();
        if (chunks.isEmpty()) {
            throw new IllegalStateException("RAG 知识库没有可向量化的 Markdown 文档");
        }
        // add() 会逐个调用 EmbeddingModel，把文本块转换成向量并放入内存向量库。
        vectorStore.add(chunks);
        PostgresKeywordDocumentStore keywordDocumentStore = keywordDocumentStoreProvider.getIfAvailable();
        if (keywordDocumentStore != null) {
            keywordDocumentStore.replaceAll(chunks);
        }
        return vectorStore;
    }

    @Bean("loveAppLocalFaqDocumentRetriever")
    public DocumentRetriever loveAppLocalFaqDocumentRetriever(
            @Qualifier("loveAppVectorStore") VectorStore vectorStore,
            RagProperties properties) {
        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(LoveRagFilterFactory.faq(null))
                .similarityThreshold(properties.getLocal().getSimilarityThreshold())
                .topK(properties.getLocal().getTopK())
                .build();
    }

    /**
     * 构造本地问答链使用的 RAG Advisor，只检索恋爱问答资料。
     *
     * <p>如果没有足够相关的资料，ContextualQueryAugmenter 会阻止模型继续生成回答，而是返回
     * emptyContextPromptTemplate 中的提示语。可选的查询转换器链与扩展器统一由
     * {@link LoveRagQueryChain} 按名称装配，三个 provider 对应的开关未启用时自动缺席。</p>
     */
    @Bean("loveAppLocalFaqRagAdvisor")
    public Advisor loveAppLocalFaqRagAdvisor(
            @Qualifier("loveAppLocalFaqDocumentRetriever") DocumentRetriever documentRetriever,
            @Qualifier("loveCompressionQueryTransformer") ObjectProvider<QueryTransformer> compressionProvider,
            @Qualifier("loveRewriteQueryTransformer") ObjectProvider<QueryTransformer> rewriteProvider,
            @Qualifier("loveExternalTranslationQueryTransformer") ObjectProvider<QueryTransformer> translationProvider,
            ObjectProvider<QueryExpander> queryExpanderProvider) {
        RetrievalAugmentationAdvisor.Builder advisorBuilder = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(false)
                        .emptyContextPromptTemplate(new org.springframework.ai.chat.prompt.PromptTemplate(
                                "抱歉，当前恋爱知识库中没有找到足够相关的资料，请换一种恋爱问题描述。"))
                        .build())
                .order(RAG_ADVISOR_ORDER);
        LoveRagQueryChain.applyToBuilder(
                advisorBuilder,
                LoveRagQueryChain.assembleQueryTransformers(compressionProvider, rewriteProvider, translationProvider),
                queryExpanderProvider);
        return advisorBuilder.build();
    }

    /**
     * 构造本地问答链使用的 RAG Advisor，只检索恋爱候选人资料。
     *
     * <p>如果没有足够相关的资料，ContextualQueryAugmenter 会阻止模型继续生成回答，而是返回
     * emptyContextPromptTemplate 中的提示语。查询转换器链与扩展器的装配方式与 FAQ Advisor 相同。</p>
     */
    @Bean("loveAppLocalCandidateRagAdvisor")
    public Advisor loveAppLocalCandidateRagAdvisor(
            @Qualifier("loveAppLocalCandidateDocumentRetriever") DocumentRetriever documentRetriever,
            @Qualifier("loveCompressionQueryTransformer") ObjectProvider<QueryTransformer> compressionProvider,
            @Qualifier("loveRewriteQueryTransformer") ObjectProvider<QueryTransformer> rewriteProvider,
            @Qualifier("loveExternalTranslationQueryTransformer") ObjectProvider<QueryTransformer> translationProvider,
            ObjectProvider<QueryExpander> queryExpanderProvider) {
        RetrievalAugmentationAdvisor.Builder advisorBuilder = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                // 没有合适候选人时仍让模型输出空 matches，而不是把普通文本强行解析成对象。
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                .order(RAG_ADVISOR_ORDER);
        LoveRagQueryChain.applyToBuilder(
                advisorBuilder,
                LoveRagQueryChain.assembleQueryTransformers(compressionProvider, rewriteProvider, translationProvider),
                queryExpanderProvider);
        return advisorBuilder.build();
    }

    @Bean("loveAppLocalCandidateDocumentRetriever")
    public DocumentRetriever loveAppLocalCandidateDocumentRetriever(
            @Qualifier("loveAppVectorStore") VectorStore vectorStore,
            RagProperties properties) {
        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(LoveRagFilterFactory.candidates())
                .similarityThreshold(properties.getLocal().getSimilarityThreshold())
                .topK(properties.getLocal().getTopK())
                .build();
    }
}
