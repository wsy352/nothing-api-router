package com.lingrui.router.service;

/**
 * 中转站身份验证服务
 */
public interface RouterAuthService {

    // 验证聊天访问秘钥
    void validateChatKey(String authorization);

    // 验证管理员访问秘钥
    void validateAdminKey(String authorization);
}
