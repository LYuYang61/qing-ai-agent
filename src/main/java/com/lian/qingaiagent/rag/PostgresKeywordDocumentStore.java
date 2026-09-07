package com.lian.qingaiagent.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 使用 PostgreSQL 关系表做关键词检索的轻量文档存储。
 *
 * <p>它与 PGVector 表分开，故意模拟“向量检索 + 传统数据存储”的混合场景；实际项目也可以
 * 将这一层替换为 MySQL、Redis 或 Elasticsearch，而不改变 HybridDocumentRetriever。</p>
 */
public class PostgresKeywordDocumentStore {

    private static final String TABLE_NAME = "qing_rag_keyword_document";

    private final JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper;

    public PostgresKeywordDocumentStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        initializeSchema();
    }

    private void initializeSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS qing_rag_keyword_document (
                    id VARCHAR(255) PRIMARY KEY,
                    content TEXT NOT NULL,
                    metadata JSONB NOT NULL
                )
                """);
    }

    /**
     * 用新的文档列表替换现有的关键词文档存储。
     *
     * <p>先清空表，再批量插入新文档。每个文档的元数据会被序列化为 JSONB 存储。</p>
     *
     * @param documents 新的文档列表
     */
    public void replaceAll(List<Document> documents) {
        jdbcTemplate.update("DELETE FROM " + TABLE_NAME);
        for (Document document : documents) {
            String metadataJson = serializeMetadata(document.getMetadata());
            jdbcTemplate.update(
                    "INSERT INTO " + TABLE_NAME + " (id, content, metadata) VALUES (?, ?, ?::jsonb)",
                    document.getId(),
                    document.getText(),
                    metadataJson);
        }
    }

    /**
     * 按关键词和元数据过滤条件检索文档。
     *
     * <p>关键词匹配使用 ILIKE，元数据过滤使用 JSONB 的 ->> 操作符。</p>
     *
     * @param query 查询对象，包含文本和上下文
     * @param topK  返回的最大文档数
     * @return 匹配的文档列表，按关键词匹配度降序排序
     */
    public List<Document> search(Query query, int topK) {
        List<String> terms = extractTerms(query.text());
        if (terms.isEmpty()) {
            return List.of();
        }

        Map<String, Object> filters = filtersFrom(query);
        String scoreExpression = terms.stream()
                .map(term -> "(CASE WHEN content ILIKE ? OR metadata::text ILIKE ? THEN 1 ELSE 0 END)")
                .collect(Collectors.joining(" + "));
        String keywordPredicate = terms.stream()
                .map(term -> "(content ILIKE ? OR metadata::text ILIKE ?)")
                .collect(Collectors.joining(" OR "));
        String metadataPredicate = filters.keySet().stream()
                .map(key -> "metadata ->> ? = ?")
                .collect(Collectors.joining(" AND "));
        String whereClause = metadataPredicate.isBlank()
                ? keywordPredicate
                : "(" + keywordPredicate + ") AND " + metadataPredicate;
        String sql = "SELECT id, content, metadata::text AS metadata_json, "
                + scoreExpression + " AS keyword_score FROM " + TABLE_NAME
                + " WHERE " + whereClause
                + " ORDER BY keyword_score DESC, id LIMIT ?";

        List<Object> parameters = new ArrayList<>();
        for (String term : terms) {
            parameters.add("%" + term + "%");
            parameters.add("%" + term + "%");
        }
        for (String term : terms) {
            parameters.add("%" + term + "%");
            parameters.add("%" + term + "%");
        }
        filters.forEach((key, value) -> {
            parameters.add(key);
            parameters.add(String.valueOf(value));
        });
        parameters.add(topK);

        return jdbcTemplate.query(sql, (resultSet, rowNumber) ->
                new Document(
                        resultSet.getString("id"),
                        resultSet.getString("content"),
                        deserializeMetadata(resultSet.getString("metadata_json"))),
                parameters.toArray());
    }

    /**
     * 从查询上下文中提取元数据过滤条件。
     *
     * <p>如果查询上下文中包含 {@link VectorStoreDocumentRetriever#FILTER_EXPRESSION}，
     * 则将其转换为键值对形式的过滤条件；否则返回空映射。</p>
     *
     * @param query 查询对象
     * @return 元数据过滤条件的键值对映射
     */
    private Map<String, Object> filtersFrom(Query query) {
        Object filter = query.context().get(VectorStoreDocumentRetriever.FILTER_EXPRESSION);
        if (filter instanceof Filter.Expression expression) {
            return LoveRagFilterFactory.equalityFilters(expression);
        }
        return Map.of();
    }

    /**
     * 从文本中提取关键词。
     *
     * <p>规则：
     * <ul>
     *   <li>按空白和标点符号拆分单词，长度大于 1 的单词作为关键词。</li>
     *   <li>如果单词包含中文字符，则按连续的两个字符生成额外的关键词。</li>
     *   <li>最多返回 12 个关键词，按出现顺序去重。</li>
     * </ul></p>
     *
     * @param text 输入文本
     * @return 提取的关键词列表
     */
    private List<String> extractTerms(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String word : text.replaceAll("[\\p{P}\\p{S}\\s]+", " ").trim().split(" ")) {
            if (word.length() > 1) {
                terms.add(word);
            }
            if (word.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF)) {
                for (int index = 0; index + 2 <= word.length() && terms.size() < 12; index++) {
                    terms.add(word.substring(index, index + 2));
                }
            }
            if (terms.size() >= 12) {
                break;
            }
        }
        return terms.stream().limit(12).toList();
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("RAG 文档元数据无法序列化", exception);
        }
    }

    private Map<String, Object> deserializeMetadata(String metadataJson) {
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("RAG 文档元数据无法反序列化", exception);
        }
    }
}
