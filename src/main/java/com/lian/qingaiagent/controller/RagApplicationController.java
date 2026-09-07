package com.lian.qingaiagent.controller;

import com.lian.qingaiagent.rag.CloudLoveRagApp;
import com.lian.qingaiagent.rag.LoveRagApp;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 第四期 RAG 实战接口；服务是否可用由对应的 RAG 配置开关控制。 */
@RestController
@Profile("dashscope")
@RequestMapping("/ai/rag")
public class RagApplicationController {

    private final ObjectProvider<LoveRagApp> localRagAppProvider;

    private final ObjectProvider<CloudLoveRagApp> cloudRagAppProvider;

    private final ObjectProvider<QueryTransformer> queryTransformerProvider;

    public RagApplicationController(ObjectProvider<LoveRagApp> localRagAppProvider,
                                    ObjectProvider<CloudLoveRagApp> cloudRagAppProvider,
                                    ObjectProvider<QueryTransformer> queryTransformerProvider) {
        this.localRagAppProvider = localRagAppProvider;
        this.cloudRagAppProvider = cloudRagAppProvider;
        this.queryTransformerProvider = queryTransformerProvider;
    }

    @GetMapping("/local/chat")
    public String localChat(@RequestParam String message,
                            @RequestParam(defaultValue = "") String status) {
        return requireLocalRagApp().chat(message, status);
    }

    @GetMapping("/local/hybrid")
    public String hybridChat(@RequestParam String message,
                             @RequestParam(defaultValue = "") String status) {
        try {
            return requireLocalRagApp().chatWithHybrid(message, status);
        } catch (IllegalStateException exception) {
            throw unavailable(exception.getMessage());
        }
    }

    @GetMapping("/query/translate")
    public String translateQuery(@RequestParam String message) {
        QueryTransformer queryTransformer = queryTransformerProvider.getIfAvailable();
        if (queryTransformer == null) {
            throw unavailable("外部翻译查询转换器未启用，请设置 qing.ai.rag.query-translation.enabled=true");
        }
        return queryTransformer.transform(new Query(message)).text();
    }

    @GetMapping("/love-match/recommend")
    public LoveRagApp.LoveMatchRecommendation recommend(@RequestParam String message) {
        return requireLocalRagApp().recommend(message);
    }

    @GetMapping("/cloud/chat")
    public String cloudChat(@RequestParam String message) {
        return requireCloudRagApp().chat(message);
    }

    private LoveRagApp requireLocalRagApp() {
        LoveRagApp app = localRagAppProvider.getIfAvailable();
        if (app == null) {
            throw unavailable("本地 RAG 未启用，请设置 qing.ai.rag.local.enabled=true");
        }
        return app;
    }

    private CloudLoveRagApp requireCloudRagApp() {
        CloudLoveRagApp app = cloudRagAppProvider.getIfAvailable();
        if (app == null) {
            throw unavailable("云 RAG 未启用，请配置知识库索引后设置 qing.ai.rag.cloud.enabled=true");
        }
        return app;
    }

    private ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

}
