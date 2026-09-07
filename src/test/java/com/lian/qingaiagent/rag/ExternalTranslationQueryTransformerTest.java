package com.lian.qingaiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.rag.Query;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExternalTranslationQueryTransformerTest {

    @Test
    void translatesQueryAndPreservesContext() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("https://translation.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://translation.test/translate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"translatedText\":\"translated query\"}", MediaType.APPLICATION_JSON));

        ExternalTranslationQueryTransformer transformer = new ExternalTranslationQueryTransformer(
                restClientBuilder.build(), "auto", "zh", "test-key");
        Query original = new Query("original query", List.of(), Map.of("scope", "test"));

        Query transformed = transformer.transform(original);

        assertEquals("translated query", transformed.text());
        assertEquals(original.history(), transformed.history());
        assertEquals(original.context(), transformed.context());
        server.verify();
    }

    @Test
    void returnsOriginalQueryWhenTranslationServiceFails() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("https://translation.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://translation.test/translate"))
                .andRespond(withBadRequest());

        ExternalTranslationQueryTransformer transformer = new ExternalTranslationQueryTransformer(
                restClientBuilder.build(), "auto", "zh", null);
        Query original = new Query("original query");

        assertSame(original, transformer.transform(original));
        server.verify();
    }
}
