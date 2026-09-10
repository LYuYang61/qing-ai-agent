package com.lian.qingaiagent.tools;

import org.springframework.util.StringUtils;

import java.net.URI;

/** 工具网络请求的最小 URL 校验，避免误把 file:// 等本地协议交给网络工具。 */
public final class ToolUrlValidator {

    private ToolUrlValidator() {
    }

    public static URI requireHttpUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            throw new IllegalArgumentException("URL 不能为空");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("URL 格式无效", exception);
        }
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || !StringUtils.hasText(uri.getHost())) {
            throw new IllegalArgumentException("只允许访问带主机名的 HTTP/HTTPS URL");
        }
        return uri;
    }
}
