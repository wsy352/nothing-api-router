package com.lingrui.router.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
/**
 * 接收调用者传来的模型别名，消息列表和流式选项
 */
@Data
public class ChatCompletionDTO {

    // 模型别名
    private String model;

    // 消息列表
    private List<ChatMessageDTO> messages;

    // 流式选项
    private Boolean stream;

    //调用者允许生成的最大回答 token 数
    @JsonProperty("max_tokens")
    private Integer maxTokens;
}
