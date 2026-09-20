package com.lingrui.router.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 火山方舟渠道配置
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "router.ark")
public class ArkProperties {

    private String alias;
    private String baseUrl;
    private String apiKey;
    private String model;
}
