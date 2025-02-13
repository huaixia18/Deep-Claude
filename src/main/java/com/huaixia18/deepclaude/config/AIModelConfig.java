package com.huaixia18.deepclaude.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.model")
public class AIModelConfig {
    private ModelConfig deepseek;
    private ModelConfig claude;

    @Data
    @ToString
    public static class ModelConfig {
        private String name;
        private String key;
        private String url;
    }
}