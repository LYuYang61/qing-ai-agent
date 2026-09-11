package com.lian.qingaiagent.mcp.dateplace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 读取约会地点目录。
 *
 * <p>把目录读取独立出来，工具只负责筛选逻辑；以后可以将目录换成数据库、远程 API
 * 或定时刷新的缓存，而不用改变 MCP 工具的输入输出协议。</p>
 */
@Component
public class DatePlaceCatalog {

    private final List<DatePlace> places;

    public DatePlaceCatalog(DatePlaceProperties properties,
                            ResourceLoader resourceLoader,
                            ObjectMapper objectMapper) {
        Resource resource = resourceLoader.getResource(properties.getCatalog());
        if (!resource.exists()) {
            throw new IllegalStateException("约会地点目录不存在：" + properties.getCatalog());
        }
        try (InputStream inputStream = resource.getInputStream()) {
            List<DatePlace> loaded = objectMapper.readValue(inputStream, new TypeReference<>() {
            });
            if (loaded == null || loaded.isEmpty()) {
                throw new IllegalStateException("约会地点目录不能为空：" + properties.getCatalog());
            }
            this.places = List.copyOf(loaded);
        }
        catch (IOException exception) {
            throw new IllegalStateException("读取约会地点目录失败：" + properties.getCatalog(), exception);
        }
    }

    public List<DatePlace> all() {
        return places;
    }
}
