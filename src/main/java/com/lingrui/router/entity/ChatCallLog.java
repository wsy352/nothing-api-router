package com.lingrui.router.entity;

import lombok.Data;

/**
 * 一次模型调用的记录，对应 chat_call_log 表中的一行
 */
@Data
public class ChatCallLog {
    private Long id;
    private String requestId;
    private Long userId;     //发起调用的用户编号，用于后续按用户统计和计费
    private Long apiKeyId;    //本次使用的秘钥编号，用于按 Key 查询用量和费用
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
