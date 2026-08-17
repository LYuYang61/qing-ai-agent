package com.lian.qingaiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoveRagFilterFactoryTest {

    @Test
    void combinesFaqTypeAndRelationshipStatus() {
        Filter.Expression expression = LoveRagFilterFactory.faq("单身");

        assertEquals(Filter.ExpressionType.AND, expression.type());
        Filter.Expression typeExpression = (Filter.Expression) expression.left();
        Filter.Expression statusExpression = (Filter.Expression) expression.right();
        assertEquals("knowledgeType", ((Filter.Key) typeExpression.left()).key());
        assertEquals("faq", ((Filter.Value) typeExpression.right()).value());
        assertEquals("status", ((Filter.Key) statusExpression.left()).key());
        assertEquals("单身", ((Filter.Value) statusExpression.right()).value());
    }

    @Test
    void rejectsUnknownRelationshipStatus() {
        assertThrows(IllegalArgumentException.class, () -> LoveRagFilterFactory.faq("暧昧"));
    }

    @Test
    void candidateFilterHasSeparateKnowledgeType() {
        Filter.Expression expression = LoveRagFilterFactory.candidates();

        assertEquals(Filter.ExpressionType.EQ, expression.type());
        assertEquals("knowledgeType", ((Filter.Key) expression.left()).key());
        assertEquals("candidate", ((Filter.Value) expression.right()).value());
    }
}
