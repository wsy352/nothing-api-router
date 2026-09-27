package com.lingrui.router.entity;

import lombok.Data;

/**
 * 一次模型调用的记录，对应 chat_call_log 表中的一行
 */
@Data
public class ChatCallLog {
    private Long id;
    private String requestId;
    private String modelAlias;
    private String channel;
    private Boolean stream;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private String status;
    private String errorCode;
    private Long durationMs;
    private java.time.LocalDateTime createdAt;
}
