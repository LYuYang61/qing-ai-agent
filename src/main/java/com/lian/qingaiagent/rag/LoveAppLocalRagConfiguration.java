package com.lian.qingaiagent.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
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

    @Bean("loveAppVectorStore")
    public SimpleVectorStore loveAppVectorStore(
            @Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel,
            LoveKnowledgeDocumentLoader documentLoader) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        List<Document> sourceDocuments = documentLoader.loadMarkdownDocuments();
        List<Document> chunks = TokenTextSplitter.builder()
                .withChunkSize(800)
                .withMinChunkSizeChars(200)
                .withMinChunkLengthToEmbed(5)
                .withKeepSeparator(true)
                .build()
                .apply(sourceDocuments);
        if (chunks.isEmpty()) {
            throw new IllegalStateException("RAG 知识库没有可向量化的 Markdown 文档");
        }
        // add() 会逐个调用 EmbeddingModel，把文本块转换成向量并放入内存向量库。
        vectorStore.add(chunks);
        return vectorStore;
    }

    @Bean("loveAppLocalFaqRagAdvisor")
    public Advisor loveAppLocalFaqRagAdvisor(
            @Qualifier("loveAppVectorStore") VectorStore vectorStore,
            RagProperties properties) {
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(LoveRagFilterFactory.faq(null))
                .similarityThreshold(properties.getLocal().getSimilarityThreshold())
                .topK(properties.getLocal().getTopK())
                .build();
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(false)
                        .emptyContextPromptTemplate(new org.springframework.ai.chat.prompt.PromptTemplate(
                                "抱歉，当前恋爱知识库中没有找到足够相关的资料，请换一种恋爱问题描述。"))
                        .build())
                .build();
    }

    @Bean("loveAppLocalCandidateRagAdvisor")
    public Advisor loveAppLocalCandidateRagAdvisor(
            @Qualifier("loveAppVectorStore") VectorStore vectorStore,
            RagProperties properties) {
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(LoveRagFilterFactory.candidates())
                .similarityThreshold(properties.getLocal().getSimilarityThreshold())
                .topK(properties.getLocal().getTopK())
                .build();
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                // 没有合适候选人时仍让模型输出空 matches，而不是把普通文本强行解析成对象。
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                .build();
    }
}
