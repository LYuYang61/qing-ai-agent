package com.lian.qingaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 受控终端工具。
 *
 * <p>Windows IDEA 与 WSL 的默认 shell 不同，因此根据实际 JVM 操作系统选择 {@code cmd.exe /c}
 * 或 {@code /bin/sh -c}。工具默认不注册，必须显式打开 terminal.enabled；即使打开也会设置超时，
 * 避免一个挂起进程占住工具调用线程。</p>
 */
public class TerminalOperationTool {

    private final int timeoutMs;

    public TerminalOperationTool(int timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("终端超时时间必须大于 0");
        }
        this.timeoutMs = timeoutMs;
    }

    @Tool(name = "executeTerminalCommand", description = "在受控的学习环境中执行一条终端命令并返回标准输出；不要执行删除、提权或泄露密钥的命令")
    public String executeTerminalCommand(@ToolParam(description = "要执行的终端命令") String command) {
        if (!StringUtils.hasText(command)) {
            return "终端执行失败：命令不能为空";
        }

        Process process = null;
        try (ExecutorService outputReader = Executors.newVirtualThreadPerTaskExecutor()) {
            process = new ProcessBuilder(shellCommand(command))
                    .redirectErrorStream(true)
                    .start();
            Process currentProcess = process;
            Future<String> output = outputReader.submit(() -> readOutput(currentProcess.getInputStream()));
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                output.cancel(true);
                return "终端执行失败：命令超过 " + timeoutMs + " ms，已强制终止";
            }
            String result = output.get(1, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                return result + "\n命令执行失败，退出码：" + process.exitValue();
            }
            return result;
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return "终端执行被中断";
        }
        catch (ExecutionException | IOException | java.util.concurrent.TimeoutException exception) {
            if (process != null) {
                process.destroyForcibly();
            }
            return "终端执行失败：" + exception.getMessage();
        }
    }

    private List<String> shellCommand(String command) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return windows ? List.of("cmd.exe", "/c", command) : List.of("/bin/sh", "-c", command);
    }

    private String readOutput(InputStream inputStream) throws IOException {
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
