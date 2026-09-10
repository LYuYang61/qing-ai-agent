package com.lian.qingaiagent.tools;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentTimeToolTest {

    @Test
    void formatsFixedTimeInRequestedZone() {
        CurrentTimeTool tool = new CurrentTimeTool(
                Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC), "Asia/Shanghai");

        assertEquals("2026-01-02 11:04:05 +08:00 Asia/Shanghai", tool.getCurrentTime("Asia/Shanghai"));
    }

    @Test
    void reportsInvalidZoneWithoutThrowingToModel() {
        CurrentTimeTool tool = new CurrentTimeTool("Asia/Shanghai");

        assertTrue(tool.getCurrentTime("not-a-zone").startsWith("时间查询失败："));
    }
}
