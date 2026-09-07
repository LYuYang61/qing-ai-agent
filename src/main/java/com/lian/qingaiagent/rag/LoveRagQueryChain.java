package com.lian.qingaiagent.rag;

import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询转换器链的公共装配逻辑。
 *
 * <p>RetrievalAugmentationAdvisor 有两个互不相同的查询前置插槽：
 * {@code queryTransformers} 是"一进一出"的顺序链，后一个转换器在前一个的输出上继续加工；
 * {@code queryExpander} 是"一进多出"的扩展器，把一个查询展开成多个检索查询。
 * FAQ、候选人和混合检索三个 Advisor 都通过本类装配，保证转换顺序只有这一份定义，
 * 不会因为各自复制代码而产生分叉。</p>
 */
public final class LoveRagQueryChain {

    private LoveRagQueryChain() {
    }

    /**
     * 按固定顺序装配查询转换器：压缩 → 重写 → 翻译。
     *
     * <p>顺序理由：压缩必须最先执行，趁追问还带有原对话上下文时改写成独立问题；
     * 重写其次，在独立问题上做面向检索系统的规范化；翻译最后，统一后续向量检索的语言。
     * 三个 provider 均按 Bean 名称限定：压缩、重写由查询增强配置按开关创建，
     * 翻译由外部翻译配置创建；对应开关未启用时 provider 解析为空，该环节自然跳过。</p>
     */
    public static List<QueryTransformer> assembleQueryTransformers(
            ObjectProvider<QueryTransformer> compressionProvider,
            ObjectProvider<QueryTransformer> rewriteProvider,
            ObjectProvider<QueryTransformer> translationProvider) {
        List<QueryTransformer> transformers = new ArrayList<>();
        compressionProvider.ifAvailable(transformers::add);
        rewriteProvider.ifAvailable(transformers::add);
        translationProvider.ifAvailable(transformers::add);
        return List.copyOf(transformers);
    }

    /**
     * 把转换器链与扩展器挂到 Advisor 构建器上；链为空时不调用
     * {@code queryTransformers}，保持与未装配时完全一致的默认行为。
     */
    public static void applyToBuilder(
            RetrievalAugmentationAdvisor.Builder builder,
            List<QueryTransformer> transformers,
            ObjectProvider<QueryExpander> queryExpanderProvider) {
        if (!transformers.isEmpty()) {
            builder.queryTransformers(transformers);
        }
        queryExpanderProvider.ifAvailable(builder::queryExpander);
    }
}
