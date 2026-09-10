package com.lian.qingaiagent.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/** 资源下载工具：只把资源写入工具 workspace 的 {@code download} 子目录。 */
public class ResourceDownloadTool {

    private static final String NAMESPACE = "download";

    private final ToolFileStorage storage;

    private final int timeoutMs;

    private final long maxDownloadBytes;

    public ResourceDownloadTool(ToolFileStorage storage, int timeoutMs, long maxDownloadBytes) {
        this.storage = storage;
        this.timeoutMs = timeoutMs;
        this.maxDownloadBytes = maxDownloadBytes;
    }

    @Tool(name = "downloadResource", description = "从 HTTP/HTTPS 地址下载资源并保存到工具 workspace")
    public String downloadResource(
            @ToolParam(description = "要下载的 HTTP/HTTPS 资源 URL") String url,
            @ToolParam(description = "保存到 download 目录下的文件名") String fileName) {
        Path target = null;
        try {
            URI uri = ToolUrlValidator.requireHttpUrl(url);
            if (timeoutMs <= 0 || maxDownloadBytes <= 0) {
                return "资源下载失败：下载工具配置无效";
            }
            target = storage.resolveForWrite(NAMESPACE, fileName);
            HttpRequest request = HttpUtil.createGet(uri.toString())
                    .setConnectionTimeout(timeoutMs)
                    .setReadTimeout(timeoutMs)
                    .setFollowRedirects(true)
                    .setMaxRedirectCount(3);
            try (HttpResponse response = request.execute()) {
                if (!response.isOk()) {
                    return "资源下载失败：HTTP 状态码 " + response.getStatus();
                }
                if (response.contentLength() > maxDownloadBytes) {
                    return "资源下载失败：响应超过大小限制 " + maxDownloadBytes + " bytes";
                }
                long bytes = copyWithLimit(response.bodyStream(), target, maxDownloadBytes);
                return "资源下载成功：" + storage.displayPath(target) + "（" + bytes + " bytes）";
            }
        }
        catch (Exception exception) {
            deleteQuietly(target);
            return "资源下载失败：" + exception.getMessage();
        }
    }

    private long copyWithLimit(InputStream input, Path target, long maxBytes) throws IOException {
        long total = 0;
        try (input; OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("响应超过大小限制 " + maxBytes + " bytes");
                }
                output.write(buffer, 0, read);
            }
        }
        return total;
    }

    private void deleteQuietly(Path target) {
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        }
        catch (IOException ignored) {
            // 清理失败不覆盖原始工具错误；下一次写入仍会经过 workspace 校验。
        }
    }
}
