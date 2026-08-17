package com.lian.qingaiagent.chatmemory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对话记忆的可配置项。
 *
 * <p>通过切换 {@code backend} 可以在内存记忆和文件记忆之间对比学习，
 * 不需要修改业务代码。</p>
 */
@ConfigurationProperties(prefix = "qing.ai.chat-memory")
public class ChatMemoryProperties {

    private String backend = "memory";

    private String fileDir = "${user.dir}/tmp/chat-memory";

    private int maxMessages = 20;

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public String getFileDir() {
        return fileDir;
    }

    public void setFileDir(String fileDir) {
        this.fileDir = fileDir;
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }
}
