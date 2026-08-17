package com.lian.qingaiagent.rag;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

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

}
