package com.lian.qingaiagent.tools;

import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 工具文件的隔离存储。
 *
 * <p>教程中的字符串拼接示例容易受到 {@code ../} 路径穿越影响。这里统一使用 {@link Path} 规范化，
 * 并检查符号链接解析后的真实路径，保证文件工具只能访问自己的 workspace。</p>
 */
public class ToolFileStorage {

    private final Path root;

    private final long maxFileBytes;

    public ToolFileStorage(Path root, long maxFileBytes) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        if (maxFileBytes <= 0) {
            throw new IllegalArgumentException("maxFileBytes 必须大于 0");
        }
        this.maxFileBytes = maxFileBytes;
    }

    public Path resolveForRead(String namespace, String fileName) throws IOException {
        Path path = resolveLexically(namespace, fileName);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("文件不存在或不是普通文件");
        }
        verifyRealPath(path, namespaceRoot(namespace));
        return path;
    }

    public Path resolveForWrite(String namespace, String fileName) throws IOException {
        Path namespaceRoot = namespaceRoot(namespace);
        Files.createDirectories(namespaceRoot);
        Path path = resolveLexically(namespace, fileName);
        Path parent = path.getParent();
        if (parent == null) {
            throw new IOException("无效的文件路径");
        }
        Files.createDirectories(parent);
        // 对已存在的目标或父目录做真实路径检查，防止 workspace 内的符号链接跳出沙箱。
        verifyRealPath(parent, namespaceRoot);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            verifyRealPath(path, namespaceRoot);
        }
        return path;
    }

    public String readUtf8(String namespace, String fileName) throws IOException {
        Path path = resolveForRead(namespace, fileName);
        checkSize(path, maxFileBytes);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public void writeUtf8(String namespace, String fileName, String content) throws IOException {
        if (content == null) {
            throw new IllegalArgumentException("文件内容不能为空");
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxFileBytes) {
            throw new IOException("文件内容超过大小限制：" + maxFileBytes + " bytes");
        }
        Path path = resolveForWrite(namespace, fileName);
        Files.write(path, bytes);
    }

    public void checkSize(Path path, long maxBytes) throws IOException {
        if (Files.size(path) > maxBytes) {
            throw new IOException("文件超过大小限制：" + maxBytes + " bytes");
        }
    }

    public String displayPath(Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    public Path getRoot() {
        return root;
    }

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    private Path namespaceRoot(String namespace) {
        if (!StringUtils.hasText(namespace) || namespace.contains("/") || namespace.contains("\\")) {
            throw new IllegalArgumentException("无效的工具文件空间");
        }
        Path namespaceRoot = root.resolve(namespace).normalize();
        if (!namespaceRoot.startsWith(root)) {
            throw new IllegalArgumentException("工具文件空间越界");
        }
        return namespaceRoot;
    }

    private Path resolveLexically(String namespace, String fileName) {
        if (!StringUtils.hasText(fileName) || fileName.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("文件名不能为空且不能包含 NUL 字符");
        }
        Path namespaceRoot = namespaceRoot(namespace);
        Path path = namespaceRoot.resolve(fileName).normalize();
        if (!path.startsWith(namespaceRoot)) {
            throw new IllegalArgumentException("文件路径不能离开工具 workspace");
        }
        return path;
    }

    private void verifyRealPath(Path path, Path base) throws IOException {
        Path realBase = base.toRealPath();
        Path realPath = path.toRealPath();
        if (!realPath.startsWith(realBase)) {
            throw new IOException("文件路径不能离开工具 workspace");
        }
    }
}
