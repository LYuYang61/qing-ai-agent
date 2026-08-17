package com.lian.qingaiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoveKnowledgeDocumentLoaderTest {

    private final LoveKnowledgeDocumentLoader loader = new LoveKnowledgeDocumentLoader();

    @Test
    void loadsFaqAndCandidateDocumentsWithMetadata() {
        List<Document> documents = loader.loadMarkdownDocuments();

        assertTrue(documents.size() >= 18);
        assertTrue(documents.stream().anyMatch(document ->
                "faq".equals(document.getMetadata().get("knowledgeType"))
                        && "单身".equals(document.getMetadata().get("status"))));
        assertTrue(documents.stream().anyMatch(document ->
                "candidate".equals(document.getMetadata().get("knowledgeType"))
                        && document.getText().contains("候选人姓名")));
    }
}
