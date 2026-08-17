package com.lian.qingaiagent.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 多模态模型和上传限制配置。 */
@ConfigurationProperties(prefix = "qing.ai.multimodal")
public class MultimodalProperties {

    /** 视觉理解模型。旧版 qwen3-vl-* 已下线，Qwen3.7 主系列原生支持视觉。 */
    private String model = "qwen3.7-plus";

    private int maxUploadBytes = 10 * 1024 * 1024;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public void setMaxUploadBytes(int maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes;
    }
}
