package com.lingrui.router.service;

import com.lingrui.router.config.RouterAuthProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 验证调用者的中转站访问秘钥
 */
@Service
public class RouterAuthService {

    @Autowired
    private RouterAuthProperties routerAuthProperties;

    public void validateApiKey(String authorization){

        String apiKey = routerAuthProperties.getApiKey();

        //服务端没有配置秘钥是，不允许放行请求
        if(apiKey == null || apiKey.isBlank()){
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "中转站尚未配置访问秘钥"
            );
        }

        //检查是否使用 Bearer 格式
        if(authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)){
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请提供有效的中转站访问秘钥"
            );
        }

        //取出 Bearer 后面的秘钥
        String providedKey = authorization.substring(7);

        //与服务端配置的秘钥比较
        if(!apiKey.equals(providedKey)){
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请提供有效的中转站访问秘钥"
            );
        }
    }
}
