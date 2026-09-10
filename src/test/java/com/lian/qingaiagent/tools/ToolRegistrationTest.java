package com.lian.qingaiagent.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.ToolCallback;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistrationTest {

    @TempDir
    Path tempDir;

    @Test
    void convertsRegisteredMethodsToSpringAiToolCallbacks() {
        ToolProperties properties = new ToolProperties();
        properties.setWorkspace(tempDir.toString());
        ToolCallback[] callbacks = new ToolRegistration().loveToolCallbacks(
                properties, new ToolFileStorage(tempDir, properties.getMaxFileBytes()));

        Set<String> names = Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertTrue(names.containsAll(Set.of(
                "readFile", "writeFile", "searchWeb", "scrapeWebPage", "downloadResource",
                "generatePdf", "getCurrentTime")));
        assertTrue(names.size() >= 7);
    }
}
