package com.lian.qingaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
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
public class LoveKnowledgeDocumentLoader implements DocumentReader {

    private static final String DOCUMENT_PATTERN = "classpath*:document/**/*.md";

    /**
     * 用带"篇"后缀的完整词元匹配文件名，避免公共前缀"恋爱常见问题和回答"里的"恋爱"
     * 抢先命中已婚篇/单身篇——第四期曾因裸子串匹配把已婚篇全部错标为"恋爱"。
     */
    private static final Map<String, String> STATUS_FILE_TOKENS = Map.of(
            "单身篇", "单身",
            "恋爱篇", "恋爱",
            "已婚篇", "已婚");

    private final PathMatchingResourcePatternResolver resourceResolver =
            new PathMatchingResourcePatternResolver();

    /**
     * 自定义 DocumentReader 入口，保留统一的 Spring AI ETL 抽取契约。
     */
    @Override
    public List<Document> get() {
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

    /** 保留第四期的业务方法名，便于已有代码和测试继续使用。 */
    public List<Document> loadMarkdownDocuments() {
        return get();
    }

    /**
     * 读取单个 Markdown 文件，按标题和段落拆分成多个 Document，并附加业务元数据。
     */
    private List<Document> readResource(Resource resource) {
        Map<String, Object> metadata = metadataFor(resource);
        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withIncludeBlockquote(true)
                .withAdditionalMetadata(metadata)
                .build();
        return new ArrayList<>(new MarkdownDocumentReader(resource, config).get());
    }

    /**
     * 根据文件路径和文件名生成业务元数据。
     *
     * <p>元数据包括：
     * <ul>
     *   <li>source：文件名</li>
     *   <li>knowledgeType：faq 或 candidate</li>
     *   <li>status：单身、恋爱、已婚（仅 faq 类型有）</li>
     * </ul>
     */
    private Map<String, Object> metadataFor(Resource resource) {
        String description = resource.getDescription().replace('\\', '/');
        String fileName = resource.getFilename() == null ? description : resource.getFilename();
        String knowledgeType = description.contains("/candidates/") || description.contains("candidates/")
                ? "candidate" : "faq";

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", fileName);
        metadata.put("sourceType", "classpath-markdown");
        metadata.put("language", "zh-CN");
        metadata.put("documentVersion", "demo-v1");
        metadata.put("knowledgeType", knowledgeType);
        if ("faq".equals(knowledgeType)) {
            metadata.put("status", findRelationshipStatus(fileName));
        }
        return metadata;
    }

    /**
     * 根据文件名判断恋爱关系状态。
     *
     * <p>三个词元（单身篇/恋爱篇/已婚篇）互斥，不会同时出现在一个文件名里，
     * 因此匹配顺序无关；都不命中时返回通用。
     */
    private String findRelationshipStatus(String fileName) {
        return STATUS_FILE_TOKENS.entrySet().stream()
                .filter(entry -> fileName.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("通用");
    }
}
