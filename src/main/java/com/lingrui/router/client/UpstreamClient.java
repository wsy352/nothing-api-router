package com.lingrui.router.client;

import com.lingrui.router.dto.ChatCompletionDTO;
import com.lingrui.router.vo.ChatCompletionVO;

/**
 * 上游模型客户端，定义向平台发起请求的能力
 */
public interface UpstreamClient {
    /**
     * 向上游平台发起非流式聊天请求
     * @param request 聊天请求
     * @return 转换为统一格式的聊天响应
     */
    ChatCompletionVO chat(ChatCompletionDTO request);
}
