package com.lian.qingaiagent.demo.invoke;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 使用 Spring AI Alibaba 自动配置的 ChatModel 调用 DashScope。
 */
@Component
@Profile("dashscope")
public class SpringAiAiInvoke implements CommandLineRunner {

    private final ChatModel dashScopeChatModel;

    public SpringAiAiInvoke(@Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel) {
        // 项目同时保留 Ollama，因此通过 bean 名称明确选择 DashScope 模型。
        this.dashScopeChatModel = dashScopeChatModel;
    }

    @Override
    public void run(String... args) {
        // 只有启用 dashscope profile 时才执行，避免默认启动阶段访问云端模型。
        String answer = dashScopeChatModel.call(new Prompt("你好，我正在学习 Spring AI。"))
                .getResult()
                .getOutput()
                .getText();
        System.out.println(answer);
    }
}
