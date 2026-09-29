package com.lingrui.router;

import com.lingrui.router.config.RouterAuthProperties;
import com.lingrui.router.service.RouterAuthService;
import com.lingrui.router.service.impl.RouterAuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class RouterAuthServiceTest {

    private RouterAuthProperties routerAuthProperties;
    private RouterAuthService routerAuthService;

    @BeforeEach
    void setUp(){

        //测试使用自己的配置，不读取 IDEA 的环境变量
        routerAuthProperties = new RouterAuthProperties();
        routerAuthProperties.setApiKey("123456");
        routerAuthProperties.setAdminKey("654321");

        routerAuthService = new RouterAuthServiceImpl();

        //没有启动 Spring ,手动将配置放入服务对象。用反射给私有字段赋值
        ReflectionTestUtils.setField(
                routerAuthService,         //目标对象，往谁身上塞
                "routerAuthProperties",    //字段名：塞进哪个字段
                routerAuthProperties       //塞什么值
        );
    }

    /**
     * 正确的秘钥应当通过验证
     */
    @Test
    void shouldAcceptCorrectKeys(){
        assertDoesNotThrow(
                () -> routerAuthService.validateChatKey("Bearer 123456")
        );

        assertDoesNotThrow(
                () -> routerAuthService.validateAdminKey("Bearer 654321")
        );
    }

    /**
     * 聊天秘钥和管理员秘钥不能混用
     */
    @Test
    void shouldRejectMixedKeys(){
        ResponseStatusException chatException = assertThrows(
                ResponseStatusException.class,
                () -> routerAuthService.validateChatKey("Bearer 654321")
        );
        assertEquals(401, chatException.getStatusCode().value());

        ResponseStatusException adminException = assertThrows(
                ResponseStatusException.class,
                () -> routerAuthService.validateAdminKey("Bearer 123456")
        );
        assertEquals(401, adminException.getStatusCode().value());
    }

    /**
     * 缺失秘钥，格式错误或秘钥错误时，应当拒绝访问
     */
    @Test
    void shouldRejectMissingOrInvalidKeys(){
        String[] invalidHeaders = {
                null,
                "",
                "123456",
                "Bearer ",
                "Bearer wrong"
        };

        for (String authorization : invalidHeaders){
            ResponseStatusException chatException = assertThrows(
                    ResponseStatusException.class,
                    () -> routerAuthService.validateChatKey(authorization)
            );
            assertEquals(401, chatException.getStatusCode().value());

            ResponseStatusException adminException = assertThrows(
                    ResponseStatusException.class,
                    () -> routerAuthService.validateAdminKey(authorization)
            );
            assertEquals(401, adminException.getStatusCode().value());
        }
    }

    /**
     * 服务端未配置管理员秘钥时，应当拒绝访问
     */
    @Test
    void shouldRejectUnconfiguredAdminKey(){
        routerAuthProperties.setAdminKey("");

        ResponseStatusException responseStatusException = assertThrows(
                ResponseStatusException.class,
                () -> routerAuthService.validateAdminKey("Bearer 654321")
        );
        assertEquals(500, responseStatusException.getStatusCode().value());
    }
}
