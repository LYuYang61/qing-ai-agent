package com.lian.qingaiagent.config;

import com.lian.qingaiagent.app.MultimodalProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 第三期扩展应用的配置属性注册。 */
@Configuration
@Profile("dashscope")
@EnableConfigurationProperties(MultimodalProperties.class)
public class StudyExtensionConfiguration {
}
