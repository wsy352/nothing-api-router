package com.lingrui.router.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 上游渠道配置，当前阶段先只配一个deepseek渠道
 */
@Data
@Component
@ConfigurationProperties(prefix = "router.deepseek")
public class ChannelProperties {
    private String baseUrl;//上游渠道的地址
    private String apiKey;//上游渠道的API Key
    private String model;//上游渠道调用的模型
    private String alias;//调用者使用的模型别名
}
