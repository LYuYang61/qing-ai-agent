package com.lian.qingaiagent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public String healthCheck() {
        // 初始化阶段用这个轻量接口确认服务已启动，也便于在 Knife4j 中观察文档扫描结果。
        return "ok";
    }
}
