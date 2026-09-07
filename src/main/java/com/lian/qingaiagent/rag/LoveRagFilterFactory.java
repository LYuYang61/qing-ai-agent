package com.lian.qingaiagent.rag;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 构造本地知识库使用的元数据过滤表达式。 */
public final class LoveRagFilterFactory {

    private static final String FAQ_KNOWLEDGE_TYPE = "faq";

    private static final String CANDIDATE_KNOWLEDGE_TYPE = "candidate";

    private static final Set<String> RELATIONSHIP_STATUSES = Set.of("单身", "恋爱", "已婚");

    private LoveRagFilterFactory() {
    }

    /**
     * 只检索恋爱问答资料；指定关系状态时再叠加状态过滤。
     */
    public static Filter.Expression faq(String status) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op faqFilter = builder.eq("knowledgeType", FAQ_KNOWLEDGE_TYPE);
        if (status == null || status.isBlank()) {
            return faqFilter.build();
        }
        if (!RELATIONSHIP_STATUSES.contains(status.trim())) {
            throw new IllegalArgumentException("关系状态只能是：单身、恋爱或已婚");
        }
        return builder.and(faqFilter, builder.eq("status", status.trim())).build();
    }

    /** 只检索候选人资料，避免把普通恋爱问答误当作候选对象。 */
    public static Filter.Expression candidates() {
        return new FilterExpressionBuilder().eq("knowledgeType", CANDIDATE_KNOWLEDGE_TYPE).build();
    }

    /**
     * 将 Spring AI 的等值过滤表达式转换成关系表可执行的键值条件。
     *
     * <p>PostgreSQL 关键词存储只演示 {@code EQ} 和 {@code AND}，因此遇到 OR、范围比较等
     * 表达式时显式失败，避免悄悄忽略过滤条件导致召回越权。</p>
     */
    public static Map<String, Object> equalityFilters(Filter.Expression expression) {
        Map<String, Object> filters = new LinkedHashMap<>();
        collectEqualityFilters(expression, filters);
        return filters;
    }

    private static void collectEqualityFilters(Filter.Expression expression, Map<String, Object> filters) {
        switch (expression.type()) {
            case EQ -> {
                if (!(expression.left() instanceof Filter.Key key)
                        || !(expression.right() instanceof Filter.Value value)) {
                    throw new IllegalArgumentException("EQ 过滤表达式必须由字段和值组成");
                }
                Object previousValue = filters.putIfAbsent(key.key(), value.value());
                if (previousValue != null && !Objects.equals(previousValue, value.value())) {
                    throw new IllegalArgumentException("同一元数据字段存在冲突的等值过滤条件：" + key.key());
                }
            }
            case AND -> {
                collectEqualityFilters(asExpression(expression.left()), filters);
                collectEqualityFilters(asExpression(expression.right()), filters);
            }
            default -> throw new IllegalArgumentException(
                    "PostgreSQL 关键词存储暂不支持过滤操作：" + expression.type());
        }
    }

    private static Filter.Expression asExpression(Filter.Operand operand) {
        if (operand instanceof Filter.Expression expression) {
            return expression;
        }
        throw new IllegalArgumentException("AND 过滤表达式的两侧必须仍是表达式");
    }

}
