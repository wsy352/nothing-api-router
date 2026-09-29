package com.lingrui.router.service.impl;

import com.lingrui.router.config.RouterAuthProperties;
import com.lingrui.router.service.RouterAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 验证调用者的中转站访问秘钥
 */
@Service
public class RouterAuthServiceImpl implements RouterAuthService {

    @Autowired
    private RouterAuthProperties routerAuthProperties;

    /**
     * 验证聊天秘钥
     */
    @Override
    public void validateChatKey(String authorization) {
        validateBearerKey(authorization, routerAuthProperties.getApiKey());
    }

    /**
     * 验证管理员秘钥
     */
    @Override
    public void validateAdminKey(String authorization) {
        validateBearerKey(authorization, routerAuthProperties.getAdminKey());
    }

    /**
     * 共用的 Bearer 秘钥校验逻辑
     */
    private void validateBearerKey(String authorization, String expectedKey){

        if(expectedKey == null || expectedKey.isBlank()){
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "服务端尚未配置访问秘钥"
            );
        }

        if(authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)){
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请提供有效的访问秘钥"
            );
        }

        String provideKey = authorization.substring(7);

        if(!expectedKey.equals(provideKey)){
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请提供有效的访问秘钥"
            );
        }
    }
}
