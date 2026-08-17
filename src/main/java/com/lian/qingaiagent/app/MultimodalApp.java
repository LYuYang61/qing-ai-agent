package com.lian.qingaiagent.app;

import com.lian.qingaiagent.advisor.StudyLoggerAdvisor;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Locale;

/**
 * 多模态示例：向视觉语言模型同时发送文字和图片，让模型理解图片后返回文字。
 *
 * <p>本示例不把图片放入对话记忆，避免将二进制数据长期写入会话文件；每次请求都是独立的多模态调用。</p>
 */
@Component
@Profile("dashscope")
public class MultimodalApp {

    private final ChatClient chatClient;
    private final MultimodalProperties properties;

    public MultimodalApp(@Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel,
                         MultimodalProperties properties) {
        // 挂上日志 Advisor：多模态响应结构与文本模型存在差异，出问题时需要观察原始 ChatResponse。
        this.chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultAdvisors(new StudyLoggerAdvisor())
                .build();
        this.properties = properties;
    }

    public String describeRemoteImage(String imageUrl, String question, String mimeType) {
        URI imageUri;
        try {
            imageUri = URI.create(imageUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("图片 URL 无法解析", exception);
        }
        String scheme = imageUri.getScheme();
        if (imageUri.getHost() == null || !("http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("图片地址必须是带主机名的 HTTP 或 HTTPS URL");
        }
        URL url;
        try {
            url = imageUri.toURL();
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException("图片 URL 无法解析", exception);
        }
        MimeType imageMimeType = imageMimeType(mimeType);
        return chatClient.prompt()
                .user(user -> user.text(question).media(imageMimeType, url))
                // 多模态请求必须显式指定视觉理解模型（qwen3.7-plus；qwen-image-* 是文生图方向，
                // 配错会返回 200 但内容为空）；
                // multiModel=true 让适配器把请求发往多模态专用端点，否则图片内容会被文本端点
                // 当作 URL 校验，服务端报 InvalidParameter: url error。
                .options(DashScopeChatOptions.builder()
                        .model(properties.getModel())
                        .multiModel(true)
                        .build())
                .call()
                .content();
    }

    public String describeUploadedImage(byte[] imageBytes, String fileName,
                                        String contentType, String question) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("上传图片不能为空");
        }
        if (imageBytes.length > properties.getMaxUploadBytes()) {
            throw new IllegalArgumentException("图片大小不能超过 "
                    + properties.getMaxUploadBytes() / 1024 / 1024 + " MB");
        }
        MimeType imageMimeType = imageMimeType(contentType);
        String safeFileName = fileName == null || fileName.isBlank() ? "uploaded-image" : fileName;
        ByteArrayResource resource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return safeFileName;
            }
        };
        Media media = Media.builder()
                .mimeType(imageMimeType)
                .data(resource)
                .name(safeFileName)
                .build();
        return chatClient.prompt()
                .user(user -> user.text(question).media(media))
                // 与远程 URL 调用相同：必须开启 multiModel 才会走多模态端点。
                .options(DashScopeChatOptions.builder()
                        .model(properties.getModel())
                        .multiModel(true)
                        .build())
                .call()
                .content();
    }

    private MimeType imageMimeType(String rawMimeType) {
        if (rawMimeType == null || rawMimeType.isBlank()) {
            throw new IllegalArgumentException("必须提供图片 MIME 类型，例如 image/png");
        }
        MimeType mimeType;
        try {
            mimeType = MimeTypeUtils.parseMimeType(rawMimeType.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("图片 MIME 类型无效: " + rawMimeType, exception);
        }
        if (!"image".equals(mimeType.getType())) {
            throw new IllegalArgumentException("只支持图片 MIME 类型，例如 image/jpeg 或 image/png");
        }
        return mimeType;
    }
}
