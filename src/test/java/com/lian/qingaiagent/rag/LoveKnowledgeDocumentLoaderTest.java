package com.lian.qingaiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoveKnowledgeDocumentLoaderTest {

    private final LoveKnowledgeDocumentLoader loader = new LoveKnowledgeDocumentLoader();

    @Test
    void loadsFaqAndCandidateDocumentsWithMetadata() {
        assertTrue(loader instanceof DocumentReader);
        List<Document> documents = loader.loadMarkdownDocuments();

        assertTrue(documents.size() >= 18);
        assertTrue(documents.stream().anyMatch(document ->
                "faq".equals(document.getMetadata().get("knowledgeType"))
                        && "单身".equals(document.getMetadata().get("status"))));
        // 回归保护:已婚篇曾因文件名前缀"恋爱"被错标为恋爱,导致 status=已婚 检索恒为空。
        assertTrue(documents.stream().anyMatch(document ->
                "faq".equals(document.getMetadata().get("knowledgeType"))
                        && "已婚".equals(document.getMetadata().get("status"))));
        assertTrue(documents.stream().anyMatch(document ->
                "faq".equals(document.getMetadata().get("knowledgeType"))
                        && "恋爱".equals(document.getMetadata().get("status"))));
        assertTrue(documents.stream().anyMatch(document ->
                "candidate".equals(document.getMetadata().get("knowledgeType"))
                        && document.getText().contains("候选人姓名")));
        assertTrue(documents.stream().allMatch(document ->
                "classpath-markdown".equals(document.getMetadata().get("sourceType"))
                        && "zh-CN".equals(document.getMetadata().get("language"))));
    }
}
