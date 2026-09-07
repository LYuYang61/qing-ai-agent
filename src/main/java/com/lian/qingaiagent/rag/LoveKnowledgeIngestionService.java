package com.lian.qingaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 统一执行恋爱知识库的 ETL 转换阶段。
 *
 * <p>读取器负责 Extract，关键词增强器是可选的 Transform，最后统一切分成适合向量化的 chunks。
 * 本地内存向量库、PGVector 和混合检索都复用同一批 chunks，避免不同存储出现内容不一致。</p>
 */
@Component
@Profile("dashscope")
public class LoveKnowledgeIngestionService {

    private final LoveKnowledgeDocumentLoader documentReader;

    private final ObjectProvider<KeywordMetadataEnricher> keywordMetadataEnricherProvider;

    public LoveKnowledgeIngestionService(
            LoveKnowledgeDocumentLoader documentReader,
            ObjectProvider<KeywordMetadataEnricher> keywordMetadataEnricherProvider) {
        this.documentReader = documentReader;
        this.keywordMetadataEnricherProvider = keywordMetadataEnricherProvider;
    }

    /**
     * 读取 Markdown 文档、可选增强元数据、切分成向量化块。
     *
     * <p>切分规则：每块约 800 字符，最少 200 字符，最少 5 个单词，保留分隔符。
     * 切分后会为每块生成稳定 ID，保证重启后仍能识别同一知识切片。</p>
     */
    public List<Document> prepareChunks() {
        List<Document> documents = documentReader.get();
        KeywordMetadataEnricher enricher = keywordMetadataEnricherProvider.getIfAvailable();
        if (enricher != null) {
            documents = enricher.apply(documents);
        }
        List<Document> chunks = TokenTextSplitter.builder()
                .withChunkSize(800)
                .withMinChunkSizeChars(200)
                .withMinChunkLengthToEmbed(5)
                .withKeepSeparator(true)
                .build()
                .apply(documents);
        return assignStableIds(chunks);
    }

    /**
     * 让内存库、PGVector 和关键词表在重启后仍能识别同一个知识切片。
     * ID 由来源、切片序号和文本内容计算，内容变化时会自然生成新 ID。
     */
    private List<Document> assignStableIds(List<Document> chunks) {
        Map<String, Integer> sourceCounters = new HashMap<>();
        return chunks.stream()
                .map(document -> {
                    String source = String.valueOf(document.getMetadata().getOrDefault("source", "unknown"));
                    int chunkIndex = sourceCounters.merge(source, 1, Integer::sum) - 1;
                    String stableKey = source + "\n" + chunkIndex + "\n" + document.getText();
                    String stableId = UUID.nameUUIDFromBytes(
                            stableKey.getBytes(StandardCharsets.UTF_8)).toString();
                    return Document.builder()
                            .id(stableId)
                            .text(document.getText())
                            .metadata(document.getMetadata())
                            .build();
                })
                .toList();
    }
}
