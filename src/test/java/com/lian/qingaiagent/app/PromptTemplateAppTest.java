package com.lian.qingaiagent.app;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTemplateAppTest {

    @Test
    void rendersPromptVariablesFromClasspathResource() {
        PromptTemplate template = new PromptTemplate(
                new ClassPathResource("prompts/love-advice.st"));

        String prompt = template.render(Map.of(
                "userName", "小明",
                "relationshipStatus", "单身",
                "tone", "温和、具体",
                "message", "如何认识志趣相投的人？"));

        assertTrue(prompt.contains("用户姓名：小明"));
        assertTrue(prompt.contains("当前关系状态：单身"));
        assertTrue(prompt.contains("如何认识志趣相投的人？"));
    }
}
