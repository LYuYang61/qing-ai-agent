package com.lian.qingaiagent.mcp.dateplace;

import java.util.List;

/** 目录中的一个约会地点；字段是服务自己的业务数据，不是 MCP 协议字段。 */
public record DatePlace(String name,
                        String city,
                        String district,
                        String category,
                        List<String> tags,
                        int budgetPerPerson,
                        double rating,
                        String address,
                        String description,
                        String bestTime) {
}
