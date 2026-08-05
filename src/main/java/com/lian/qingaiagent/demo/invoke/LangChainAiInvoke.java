package com.lian.qingaiagent.demo.invoke;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;

/**
 * 使用 LangChain4j 的 DashScope 模型适配器调用通义千问。
 */
public final class LangChainAiInvoke {

    private LangChainAiInvoke() {
    }

    public static String call(String question) {
        // 社区适配器将 DashScope SDK 封装为 LangChain4j 的 ChatModel。
        ChatModel qwenChatModel = QwenChatModel.builder()
                .apiKey(DashScopeApiKey.getRequired())
                .modelName("qwen3.7-max")
                .build();
        return qwenChatModel.chat(question);
    }

    public static void main(String[] args) {
        System.out.println(call("我是 Java 开发者，正在学习 LangChain4j。"));
    }
}
