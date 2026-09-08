package com.lian.qingaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

/**
 * 查询转换器的日志装饰器：委托给真实转换器，并把输入与输出打印到 INFO。
 *
 * <p>Spring AI 内置转换器只在 DEBUG 级别打日志，默认配置下看不到改写结果；
 * 真机验收查询压缩、重写时依赖这里把每次加工过程暴露出来。装饰器不改变任何语义，
 * 只透传输入与输出。</p>
 */
@Slf4j
public class LoggingQueryTransformer implements QueryTransformer {

    private final String label;

    private final QueryTransformer delegate;

    public LoggingQueryTransformer(String label, QueryTransformer delegate) {
        this.label = label;
        this.delegate = delegate;
    }

    @Override
    public Query transform(Query query) {
        Query transformed = delegate.transform(query);
        log.info("[{}] 查询转换：{} -> {}", label, query.text(), transformed.text());
        return transformed;
    }
}
