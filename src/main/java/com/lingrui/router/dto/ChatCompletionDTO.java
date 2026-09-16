package com.lingrui.router.dto;

import lombok.Data;

import java.util.List;
/**
 * 接收调用者传来的模型别名，消息列表和流式选项
 */
@Data
public class ChatCompletionDTO {
    private String model;// 模型别名
    private List<ChatMessageDTO> messages;// 消息列表
    private Boolean stream;// 流式选项
}
