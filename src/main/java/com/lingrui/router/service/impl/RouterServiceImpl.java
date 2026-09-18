package com.lingrui.router.service.impl;

import com.lingrui.router.client.UpstreamClient;
import com.lingrui.router.client.impl.DeepseekClientImpl;
import com.lingrui.router.dto.ChatCompletionDTO;
import com.lingrui.router.dto.ChatMessageDTO;
import com.lingrui.router.service.RouterService;
import com.lingrui.router.vo.ChatChoiceVO;
import com.lingrui.router.vo.ChatCompletionVO;
import com.lingrui.router.vo.ChatMessageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 路由服务实现类，负责组织请求处理流程
 */
@Service
public class RouterServiceImpl implements RouterService {

    //上游模型客户端，当前使用deepseek实现
    @Autowired
    private UpstreamClient upstreamClient;


    /**
     * 处理非流式聊天请求
     * @param request 调用者传入的聊天请求
     * @return 统一格式的聊天响应
     */
    @Override
    public ChatCompletionVO chat(ChatCompletionDTO request) {
        //调用客户端，由客户端向deepseek发送请求并读取回答
        ChatCompletionVO response = upstreamClient.chat(request);
        return response;
    }
}
