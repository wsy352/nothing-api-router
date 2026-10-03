package com.lingrui.router.entity;

/**
 * 用户的API秘钥记录
 */

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RouterApiKey {

    private Long id;

    private Long userId;

    private String keyName;

    private String keyHash;

    private String keyPrefix;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
