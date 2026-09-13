package com.lian.qingaiagent.agent;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 注册 AI 超级智能体配置；仅在 DashScope Profile 下启用。 */
@Configuration
@Profile("dashscope")
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfiguration {
}
