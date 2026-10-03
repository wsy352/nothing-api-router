package com.lingrui.router.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 中转站用户
 */
@Data
public class RouterUser {

    private Long id;

    private String username;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
