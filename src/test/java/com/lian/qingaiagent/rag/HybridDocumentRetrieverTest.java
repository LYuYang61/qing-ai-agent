package com.lian.qingaiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridDocumentRetrieverTest {

    @Test
    void mergesDuplicateDocumentsFromBothRetrievalChannels() {
        Document vectorOnly = new Document("vector-only", "vector result", Map.of());
        Document shared = new Document("shared", "shared result", Map.of());
        Document keywordOnly = new Document("keyword-only", "keyword result", Map.of());
        DocumentRetriever vectorRetriever = mock(DocumentRetriever.class);
        PostgresKeywordDocumentStore keywordStore = mock(PostgresKeywordDocumentStore.class);
        Query query = new Query("沟通");
        when(vectorRetriever.retrieve(query)).thenReturn(List.of(vectorOnly, shared));
        when(keywordStore.search(query, 4)).thenReturn(List.of(shared, keywordOnly));

        HybridDocumentRetriever retriever = new HybridDocumentRetriever(
                vectorRetriever, keywordStore, 4, 3, 60);

        List<Document> results = retriever.retrieve(query);

        assertEquals(List.of("shared", "vector-only", "keyword-only"),
                results.stream().map(Document::getId).toList());
        assertEquals("vector+keyword", results.get(0).getMetadata().get("retrievalSource"));
        // 回归保护:RRF 得分必须写入 Document.score(不能为 null),
        // 否则下游 ConcatenationDocumentJoiner 按 score(null→0)重排时会打乱 RRF 顺序。
        results.forEach(document -> assertNotNull(document.getScore()));
        assertTrue(results.get(0).getScore() > results.get(1).getScore());
        assertTrue(results.get(1).getScore() > results.get(2).getScore());
    }
}
