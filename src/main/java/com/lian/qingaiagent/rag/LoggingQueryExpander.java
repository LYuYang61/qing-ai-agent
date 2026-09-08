package com.lian.qingaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 查询扩展器的日志装饰器：委托给真实扩展器，并把原查询与全部变体打印到 INFO。
 *
 * <p>多查询扩展的结果默认不可见，这里把"一进多出"的完整清单暴露出来，
 * 便于真机验收时核对变体数量与 include-original 配置是否生效。</p>
 */
@Slf4j
public class LoggingQueryExpander implements QueryExpander {

    private final String label;

    private final QueryExpander delegate;

    public LoggingQueryExpander(String label, QueryExpander delegate) {
        this.label = label;
        this.delegate = delegate;
    }

    @Override
    public List<Query> expand(Query query) {
        List<Query> expanded = delegate.expand(query);
        log.info("[{}] 查询扩展：{} -> [{}]", label, query.text(),
                expanded.stream().map(Query::text).collect(Collectors.joining(" | ")));
        return expanded;
    }
}
