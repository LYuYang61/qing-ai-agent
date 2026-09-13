package com.lian.qingaiagent.agent;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 保存等待人工输入的 Agent 实例。
 *
 * <p>这是学习项目中的内存实现。生产环境应使用带 TTL 的持久化会话存储，并在存储层绑定用户
 * 身份，避免只凭 runId 就能继续别人的任务。</p>
 */
@Component
@Profile("dashscope")
public class AgentRunStore {

    private final AgentProperties properties;
    private final Map<String, Entry> runs = new LinkedHashMap<>();

    public AgentRunStore(AgentProperties properties) {
        this.properties = properties;
    }

    public synchronized void put(String runId, YuManus agent) {
        purgeExpired();
        if (runs.size() >= properties.getMaxActiveRuns()) {
            throw new IllegalStateException("等待人工输入的智能体任务已达到上限，请稍后重试");
        }
        runs.put(runId, new Entry(agent, Instant.now()));
    }

    public synchronized YuManus get(String runId) {
        purgeExpired();
        Entry entry = runs.get(runId);
        if (entry == null) {
            throw new NoSuchElementException("找不到可继续的智能体任务：" + runId);
        }
        entry.lastAccess = Instant.now();
        return entry.agent;
    }

    public synchronized void remove(String runId) {
        runs.remove(runId);
    }

    private void purgeExpired() {
        Instant deadline = Instant.now().minus(properties.getRunTtl());
        Iterator<Map.Entry<String, Entry>> iterator = runs.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().lastAccess.isBefore(deadline)) {
                iterator.remove();
            }
        }
    }

    private static final class Entry {

        private final YuManus agent;
        private Instant lastAccess;

        private Entry(YuManus agent, Instant lastAccess) {
            this.agent = agent;
            this.lastAccess = lastAccess;
        }
    }
}
