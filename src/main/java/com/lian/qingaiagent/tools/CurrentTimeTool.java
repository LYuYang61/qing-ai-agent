package com.lian.qingaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 教程之外的自定义工具：查询当前时间。
 *
 * <p>时间会随请求变化，属于模型不能可靠凭记忆完成的实时信息；通过 {@link Clock} 注入也便于单元测试固定时间。</p>
 */
public class CurrentTimeTool {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX VV");

    private final Clock clock;

    private final ZoneId defaultZone;

    public CurrentTimeTool(String defaultZone) {
        this(Clock.systemUTC(), defaultZone);
    }

    CurrentTimeTool(Clock clock, String defaultZone) {
        this.clock = clock;
        this.defaultZone = ZoneId.of(defaultZone);
    }

    @Tool(name = "getCurrentTime", description = "查询当前日期和时间；如果不指定时区，默认使用 Asia/Shanghai")
    public String getCurrentTime(@ToolParam(required = false, description = "IANA 时区，例如 Asia/Shanghai 或 UTC") String zoneId) {
        try {
            ZoneId zone = StringUtils.hasText(zoneId) ? ZoneId.of(zoneId) : defaultZone;
            return ZonedDateTime.now(clock.withZone(zone)).format(FORMATTER);
        }
        catch (RuntimeException exception) {
            return "时间查询失败：时区无效，请使用 IANA 时区名称，例如 Asia/Shanghai 或 UTC";
        }
    }
}
