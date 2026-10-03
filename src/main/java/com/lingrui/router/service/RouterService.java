package com.lingrui.router.service;

import com.lingrui.router.context.RouterAuthContext;
import com.lingrui.router.dto.ChatCompletionDTO;
import com.lingrui.router.vo.ChatCompletionVO;

import java.util.function.Consumer;

public interface RouterService {
    /**
     * 处理非流式聊天请求
     * @param request 调用者传入的聊天请求
     * @param routerAuthContext 服务端验证得到的调用者身份
     * @return 统一格式的聊天响应
     */
    ChatCompletionVO chat(ChatCompletionDTO request, RouterAuthContext routerAuthContext);

    /**
     * 处理流式聊天请求
     * @param request 调用者传入的聊天请求
     * @param routerAuthContext 服务端验证得到的调用者身份
     * @param eventConsumer 收到事件后执行的动作
     */
    void streamChat(ChatCompletionDTO request,RouterAuthContext routerAuthContext, Consumer<String> eventConsumer);


}
