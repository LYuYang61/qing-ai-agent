package com.lian.qingaiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 学习项目默认不连接数据库；第五期启用 PGVector 时由 LoveAppPostgresConfiguration
 * 根据 Profile 手动创建 DataSource，避免仅因为 PostgreSQL 驱动存在就要求默认连接配置。
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class QingAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(QingAiAgentApplication.class, args);
    }

}
