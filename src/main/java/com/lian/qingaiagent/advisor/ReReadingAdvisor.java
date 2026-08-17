package com.lian.qingaiagent.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * 教学用 Re-reading Advisor：在请求发送前让模型再次阅读原问题。
 */
public class ReReadingAdvisor implements CallAdvisor, StreamAdvisor {

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private ChatClientRequest rewrite(ChatClientRequest request) {
        String userText = request.prompt().getUserMessage().getText();
        String rewrittenText = """
                %s

                请再次阅读上面的用户问题，逐步检查你的理解后再回答。
                """.formatted(userText);
        Prompt rewrittenPrompt = request.prompt().augmentUserMessage(rewrittenText);
        return new ChatClientRequest(rewrittenPrompt, request.context());
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(rewrite(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(rewrite(request));
    }
}
