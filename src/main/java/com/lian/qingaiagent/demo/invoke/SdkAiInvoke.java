package com.lian.qingaiagent.demo.invoke;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.JsonUtils;

import java.util.List;

/**
 * 使用阿里云 DashScope Java SDK 调用通义千问。
 */
public final class SdkAiInvoke {

    private SdkAiInvoke() {
    }

    public static GenerationResult callWithMessage(String question)
            throws ApiException, NoApiKeyException, InputRequiredException {
        // SDK 使用 system 和 user 两类消息表达提示词上下文。
        Message systemMessage = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content("You are a helpful assistant.")
                .build();
        Message userMessage = Message.builder()
                .role(Role.USER.getValue())
                .content(question)
                .build();
        // GenerationParam 描述本次请求的密钥、模型、消息以及返回格式。
        GenerationParam parameter = GenerationParam.builder()
                .apiKey(DashScopeApiKey.getRequired())
                .model("qwen3.7-max")
                .messages(List.of(systemMessage, userMessage))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        // Generation 负责发送请求并返回结构化的 GenerationResult。
        return new Generation().call(parameter);
    }

    public static void main(String[] args) {
        try {
            System.out.println(JsonUtils.toJson(callWithMessage("你好，我正在学习 Java AI 应用开发。")));
        } catch (ApiException | NoApiKeyException | InputRequiredException exception) {
            throw new IllegalStateException("DashScope SDK 调用失败", exception);
        }
    }
}
