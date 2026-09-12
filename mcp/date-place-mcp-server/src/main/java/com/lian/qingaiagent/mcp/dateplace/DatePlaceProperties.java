package com.lian.qingaiagent.mcp.dateplace;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 约会地点目录的外部配置。目录位置由服务部署者控制，不由模型传入。 */
@ConfigurationProperties(prefix = "mcp.date-place")
public class DatePlaceProperties {

    private String catalog = "classpath:date-places.json";

    private int maxResults = 10;

    public String getCatalog() {
        return catalog;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }
}
