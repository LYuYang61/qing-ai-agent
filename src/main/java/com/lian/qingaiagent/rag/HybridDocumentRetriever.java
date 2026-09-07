package com.lian.qingaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 顺序执行向量检索和关键词检索，并用 Reciprocal Rank Fusion 合并结果。
 *
 * <p>两路检索在当前线程依次执行（学习项目规模小，无需并行化）；输出文档携带 RRF 总分，
 * 因为下游 ConcatenationDocumentJoiner 会按 Document.score 重排（详见 retrieve 方法注释）。</p>
 */
public class HybridDocumentRetriever implements DocumentRetriever {

    private final DocumentRetriever vectorRetriever;

    private final PostgresKeywordDocumentStore keywordDocumentStore;

    private final int keywordTopK;

    private final int resultTopK;

    private final int reciprocalRankConstant;

    public HybridDocumentRetriever(
            DocumentRetriever vectorRetriever,
            PostgresKeywordDocumentStore keywordDocumentStore,
            int keywordTopK,
            int resultTopK,
            int reciprocalRankConstant) {
        this.vectorRetriever = vectorRetriever;
        this.keywordDocumentStore = keywordDocumentStore;
        this.keywordTopK = keywordTopK;
        this.resultTopK = resultTopK;
        this.reciprocalRankConstant = reciprocalRankConstant;
    }

    /**
     * 执行混合检索：先依次执行向量检索和关键词检索，再按 Reciprocal Rank Fusion 合并结果。
     *
     * <p>如果同一文档在向量检索和关键词检索中都出现，贡献分数会累加；否则只取单次贡献。</p>
     *
     * @param query 检索查询
     * @return 排序后的文档列表，按得分从高到低，最多返回 resultTopK 个文档
     */
    @Override
    public List<Document> retrieve(Query query) {
        List<Document> vectorDocuments = vectorRetriever.retrieve(query);
        List<Document> keywordDocuments = keywordDocumentStore.search(query, keywordTopK);
        Map<String, RankedDocument> rankedDocuments = new LinkedHashMap<>();
        addRankedDocuments(rankedDocuments, vectorDocuments, "vector");
        addRankedDocuments(rankedDocuments, keywordDocuments, "keyword");
        return rankedDocuments.values().stream()
                .sorted(Comparator.comparingDouble(RankedDocument::score).reversed())
                .limit(resultTopK)
                // RRF 总分必须写入 Document.score:RetrievalAugmentationAdvisor 内部的
                // ConcatenationDocumentJoiner 会按 score 降序重排去重后的文档(score 为
                // null 时按 0 处理,排序退化为 HashMap 迭代序,RRF 顺序会被悄悄打乱)。
                .map(ranked -> Document.builder()
                        .id(ranked.document().getId())
                        .text(ranked.document().getText())
                        .metadata(ranked.document().getMetadata())
                        .score(ranked.score())
                        .build())
                .toList();
    }

    /**
     * 将检索结果按 Reciprocal Rank Fusion 计算得分，并合并到 rankedDocuments。
     *
     * <p>如果同一文档在向量检索和关键词检索中都出现，贡献分数会累加；否则只取单次贡献。</p>
     *
     * @param rankedDocuments 已有的文档得分映射
     * @param documents       新的检索结果列表
     * @param retrievalSource 检索来源标识（"vector" 或 "keyword"）
     */
    private void addRankedDocuments(
            Map<String, RankedDocument> rankedDocuments,
            List<Document> documents,
            String retrievalSource) {
        for (int index = 0; index < documents.size(); index++) {
            Document document = documents.get(index);
            String key = document.getId() == null ? document.getText() : document.getId();
            double contribution = 1.0 / (reciprocalRankConstant + index + 1);
            RankedDocument current = rankedDocuments.get(key);
            if (current == null) {
                Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
                metadata.put("retrievalSource", retrievalSource);
                rankedDocuments.put(key, new RankedDocument(
                        new Document(document.getId(), document.getText(), metadata),
                        contribution));
            } else {
                current.addScore(contribution);
                current.addSource(retrievalSource);
            }
        }
    }

    private static class RankedDocument {

        private final Document document;

        private double score;

        private final List<String> sources = new ArrayList<>();

        private RankedDocument(Document document, double score) {
            this.document = document;
            this.score = score;
            this.sources.add(String.valueOf(document.getMetadata().get("retrievalSource")));
        }

        private void addScore(double contribution) {
            score += contribution;
            document.getMetadata().put("retrievalSource", String.join("+", sources));
        }

        private void addSource(String source) {
            if (!sources.contains(source)) {
                sources.add(source);
                document.getMetadata().put("retrievalSource", String.join("+", sources));
            }
        }

        private Document document() {
            return document;
        }

        private double score() {
            return score;
        }
    }
}
