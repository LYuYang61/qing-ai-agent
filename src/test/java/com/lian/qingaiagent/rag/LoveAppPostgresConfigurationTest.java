package com.lian.qingaiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class LoveAppPostgresConfigurationTest {

    @Test
    void splitsEmbeddingRequestsByTenDocuments() {
        VectorStore vectorStore = mock(VectorStore.class);
        List<Document> documents = documents(21);

        LoveAppPostgresConfiguration.addDocumentsInBatches(vectorStore, documents);

        var calls = org.mockito.Mockito.mockingDetails(vectorStore).getInvocations().stream()
                .filter(invocation -> "add".equals(invocation.getMethod().getName()))
                .toList();
        assertEquals(List.of(10, 10, 1), calls.stream()
                .map(invocation -> ((List<?>) invocation.getArguments()[0]).size())
                .toList());
        verify(vectorStore, times(3)).add(anyList());
        verifyNoMoreInteractions(vectorStore);
    }

    @Test
    void doesNotCallVectorStoreForEmptyDocuments() {
        VectorStore vectorStore = mock(VectorStore.class);

        LoveAppPostgresConfiguration.addDocumentsInBatches(vectorStore, List.of());

        verifyNoMoreInteractions(vectorStore);
    }

    private List<Document> documents(int count) {
        List<Document> documents = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            documents.add(new Document(
                    "content-" + index,
                    Map.of("source", "document-" + index)));
        }
        return documents;
    }
}
