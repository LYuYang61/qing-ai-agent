package com.lian.qingaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 classpath 下的 Markdown 恋爱资料转换成 Spring AI {@link Document}。
 *
 * <p>MarkdownDocumentReader 会按标题和段落拆出文档；这里额外写入业务元数据，
 * 后续向量检索可以用它区分问答资料和候选人资料，也可以按单身、恋爱、已婚状态过滤。</p>
 */
@Component
@Profile("dashscope")
public class LoveKnowledgeDocumentLoader {

    private static final String DOCUMENT_PATTERN = "classpath*:document/**/*.md";

    private static final List<String> RELATIONSHIP_STATUSES = List.of("单身", "恋爱", "已婚");

    private final PathMatchingResourcePatternResolver resourceResolver =
            new PathMatchingResourcePatternResolver();

    public List<Document> loadMarkdownDocuments() {
        try {
            Resource[] resources = resourceResolver.getResources(DOCUMENT_PATTERN);
            return Arrays.stream(resources)
                    .sorted((left, right) -> left.getDescription().compareTo(right.getDescription()))
                    .flatMap(resource -> readResource(resource).stream())
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 classpath 下的 RAG Markdown 文档", exception);
        }
    }

    private List<Document> readResource(Resource resource) {
        Map<String, Object> metadata = metadataFor(resource);
        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withIncludeBlockquote(true)
                .withAdditionalMetadata(metadata)
                .build();
        return new ArrayList<>(new MarkdownDocumentReader(resource, config).get());
    }

    private Map<String, Object> metadataFor(Resource resource) {
        String description = resource.getDescription().replace('\\', '/');
        String fileName = resource.getFilename() == null ? description : resource.getFilename();
        String knowledgeType = description.contains("/candidates/") || description.contains("candidates/")
                ? "candidate" : "faq";

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", fileName);
        metadata.put("knowledgeType", knowledgeType);
        if ("faq".equals(knowledgeType)) {
            metadata.put("status", findRelationshipStatus(fileName));
        }
        return metadata;
    }

    private String findRelationshipStatus(String fileName) {
        return RELATIONSHIP_STATUSES.stream()
                .filter(fileName::contains)
                .findFirst()
                .orElse("通用");
    }
}
