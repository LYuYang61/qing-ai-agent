package com.lian.qingaiagent.controller;

import com.lian.qingaiagent.app.LoveApp;
import com.lian.qingaiagent.app.MultimodalApp;
import com.lian.qingaiagent.app.PromptTemplateApp;
import com.lian.qingaiagent.app.StudyPlannerApp;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.IOException;

/**
 * 本节应用接口。接口只在 dashscope Profile 下注册，避免默认 Ollama 配置启动时触发云模型依赖。
 */
@RestController
@Profile("dashscope")
@RequestMapping("/ai")
public class AiApplicationController {

    private final LoveApp loveApp;
    private final StudyPlannerApp studyPlannerApp;
    private final PromptTemplateApp promptTemplateApp;
    private final MultimodalApp multimodalApp;

    public AiApplicationController(LoveApp loveApp, StudyPlannerApp studyPlannerApp,
                                   PromptTemplateApp promptTemplateApp,
                                   MultimodalApp multimodalApp) {
        this.loveApp = loveApp;
        this.studyPlannerApp = studyPlannerApp;
        this.promptTemplateApp = promptTemplateApp;
        this.multimodalApp = multimodalApp;
    }

    @GetMapping("/love/chat")
    public String loveChat(@RequestParam String message, @RequestParam String chatId) {
        return loveApp.chat(message, chatId);
    }

    @GetMapping(value = "/love/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> loveStream(@RequestParam String message, @RequestParam String chatId) {
        return loveApp.stream(message, chatId);
    }

    @GetMapping("/love/report")
    public LoveApp.LoveReport loveReport(@RequestParam String message, @RequestParam String chatId) {
        return loveApp.chatWithReport(message, chatId);
    }

    @GetMapping("/love/re-reading")
    public String loveReReading(@RequestParam String message, @RequestParam String chatId) {
        return loveApp.chatWithReReading(message, chatId);
    }

    @GetMapping("/love/rag")
    public String loveRag(@RequestParam String message,
                          @RequestParam String chatId,
                          @RequestParam(defaultValue = "") String status) {
        return loveApp.chatWithRag(message, chatId, status);
    }

    @GetMapping("/study/chat")
    public String studyChat(@RequestParam String message, @RequestParam String chatId) {
        return studyPlannerApp.chat(message, chatId);
    }

    @GetMapping("/study/plan")
    public StudyPlannerApp.StudyPlan studyPlan(@RequestParam String message,
                                                @RequestParam String chatId) {
        return studyPlannerApp.createPlan(message, chatId);
    }

    @GetMapping("/love/template/render")
    public String renderLovePrompt(@RequestParam(defaultValue = "同学") String userName,
                                   @RequestParam(defaultValue = "恋爱中") String relationshipStatus,
                                   @RequestParam(defaultValue = "温和、具体") String tone,
                                   @RequestParam String message) {
        return promptTemplateApp.render(userName, relationshipStatus, tone, message);
    }

    @GetMapping("/love/template/chat")
    public String loveTemplateChat(@RequestParam(defaultValue = "同学") String userName,
                                   @RequestParam(defaultValue = "恋爱中") String relationshipStatus,
                                   @RequestParam(defaultValue = "温和、具体") String tone,
                                   @RequestParam String message,
                                   @RequestParam String chatId) {
        return promptTemplateApp.chat(userName, relationshipStatus, tone, message, chatId);
    }

    @GetMapping("/multimodal/describe-url")
    public String describeRemoteImage(@RequestParam String imageUrl,
                                      @RequestParam String question,
                                      @RequestParam(defaultValue = "image/jpeg") String mimeType) {
        return multimodalApp.describeRemoteImage(imageUrl, question, mimeType);
    }

    @PostMapping(value = "/multimodal/describe-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String describeUploadedImage(@RequestPart("image") MultipartFile image,
                                        @RequestParam String question) throws IOException {
        return multimodalApp.describeUploadedImage(
                image.getBytes(), image.getOriginalFilename(), image.getContentType(), question);
    }
}
