package com.lian.qingaiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * 查询转换器链装配的行为验证：固定顺序、空链等价于未装配、三个 Advisor 走同一份装配逻辑。
 */
class LoveRagQueryChainTest {

    @Test
    void assemblesTransformersInCompressionRewriteTranslationOrder() {
        QueryTransformer compression = mock(QueryTransformer.class);
        QueryTransformer rewrite = mock(QueryTransformer.class);
        QueryTransformer translation = mock(QueryTransformer.class);

        List<QueryTransformer> chain = LoveRagQueryChain.assembleQueryTransformers(
                providerOf(compression), providerOf(rewrite), providerOf(translation));

        assertEquals(List.of(compression, rewrite, translation), chain);
    }

    @Test
    void skipsAbsentSlotsAndKeepsRelativeOrder() {
        QueryTransformer compression = mock(QueryTransformer.class);
        QueryTransformer translation = mock(QueryTransformer.class);

        // 重写开关未启用：该槽位缺席时直接跳过，剩余转换器仍保持压缩在前、翻译在后。
        List<QueryTransformer> chain = LoveRagQueryChain.assembleQueryTransformers(
                providerOf(compression), emptyProvider(), providerOf(translation));

        assertEquals(List.of(compression, translation), chain);
    }

    @Test
    void returnsEmptyChainWhenNoTransformerBeanExists() {
        assertTrue(LoveRagQueryChain.assembleQueryTransformers(
                emptyProvider(), emptyProvider(), emptyProvider()).isEmpty());
    }

    @Test
    void appliesChainAndExpanderToAdvisorBuilder() throws Exception {
        QueryTransformer translation = mock(QueryTransformer.class);
        QueryExpander expander = mock(QueryExpander.class);

        RetrievalAugmentationAdvisor advisor = buildAdvisor(providerOf(translation), providerOf(expander));

        assertEquals(List.of(translation), readField(advisor, "queryTransformers"));
        assertSame(expander, readField(advisor, "queryExpander"));
    }

    @Test
    void leavesAdvisorUnchangedWhenChainEmptyAndNoExpander() throws Exception {
        RetrievalAugmentationAdvisor configured = buildAdvisor(emptyProvider(), emptyProvider());
        RetrievalAugmentationAdvisor untouched = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(mock(DocumentRetriever.class))
                .build();

        // 空链 + 无扩展器时不得触碰构建器：装配后的 Advisor 与完全未装配的字段保持一致。
        assertEquals(readField(untouched, "queryTransformers"), readField(configured, "queryTransformers"));
        assertEquals(readField(untouched, "queryExpander"), readField(configured, "queryExpander"));
    }

    @Test
    void faqCandidateAndHybridAdvisorsShareTheSameChainAssembly() throws Exception {
        QueryTransformer translation = mock(QueryTransformer.class);
        ObjectProvider<QueryTransformer> compression = emptyProvider();
        ObjectProvider<QueryTransformer> rewrite = emptyProvider();
        ObjectProvider<QueryTransformer> translationProvider = providerOf(translation);
        ObjectProvider<QueryExpander> noExpander = emptyProvider();
        DocumentRetriever retriever = mock(DocumentRetriever.class);

        Advisor faq = new LoveAppLocalRagConfiguration().loveAppLocalFaqRagAdvisor(
                retriever, compression, rewrite, translationProvider, noExpander);
        Advisor candidate = new LoveAppLocalRagConfiguration().loveAppLocalCandidateRagAdvisor(
                retriever, compression, rewrite, translationProvider, noExpander);
        Advisor hybrid = new LoveAppPostgresConfiguration().loveAppHybridRagAdvisor(
                retriever, compression, rewrite, translationProvider, noExpander);

        for (Advisor advisor : List.of(faq, candidate, hybrid)) {
            assertEquals(List.of(translation), readField(advisor, "queryTransformers"));
        }
    }

    private RetrievalAugmentationAdvisor buildAdvisor(
            ObjectProvider<QueryTransformer> translationProvider,
            ObjectProvider<QueryExpander> queryExpanderProvider) {
        RetrievalAugmentationAdvisor.Builder builder = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(mock(DocumentRetriever.class));
        LoveRagQueryChain.applyToBuilder(
                builder,
                LoveRagQueryChain.assembleQueryTransformers(emptyProvider(), emptyProvider(), translationProvider),
                queryExpanderProvider);
        return builder.build();
    }

    /**
     * 模拟"存在指定 Bean"的 ObjectProvider。
     * ifAvailable 是接口默认方法，Mockito 会拦截它而不会回退到 getIfAvailable 的打桩，
     * 因此必须对 ifAvailable 显式打桩，否则"存在"场景会被误判为"不存在"。
     */
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        doAnswer(invocation -> {
            Consumer<T> consumer = invocation.getArgument(0);
            consumer.accept(bean);
            return null;
        }).when(provider).ifAvailable(any());
        return provider;
    }

    /** 默认 mock 对 ifAvailable 不执行任何动作，等价于"对应名称的 Bean 不存在"。 */
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyProvider() {
        return mock(ObjectProvider.class);
    }

    /** RetrievalAugmentationAdvisor 未公开转换器链的读取接口，测试用反射核对最终装配结果。 */
    private static Object readField(Object advisor, String name) throws Exception {
        Field field = RetrievalAugmentationAdvisor.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(advisor);
    }
}
