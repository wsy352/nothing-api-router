package com.lingrui.router.service;

import com.lingrui.router.dto.ChatCompletionDTO;
import com.lingrui.router.vo.ChatCompletionVO;

import java.util.function.Consumer;

public interface RouterService {
    /**
     * 处理非流式聊天请求
     * @param request 调用者传入的聊天请求
     * @return 统一格式的聊天响应
     */
    ChatCompletionVO chat(ChatCompletionDTO request);

    /**
     * 处理流式聊天请求
     * @param request 调用者传入的聊天请求
     * @param eventConsumer 收到事件后执行的 动作
     */
    void streamChat(ChatCompletionDTO request, Consumer<String> eventConsumer);


}
