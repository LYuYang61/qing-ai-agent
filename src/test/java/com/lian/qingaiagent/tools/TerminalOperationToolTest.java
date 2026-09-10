package com.lian.qingaiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalOperationToolTest {

    @Test
    void executesSimpleCrossPlatformShellCommand() {
        String result = new TerminalOperationTool(3000).executeTerminalCommand("echo qing-tool-test");

        assertTrue(result.toLowerCase().contains("qing-tool-test"));
    }
}
