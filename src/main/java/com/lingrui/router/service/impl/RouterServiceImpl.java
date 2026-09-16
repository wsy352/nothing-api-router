package com.lingrui.router.service.impl;

import com.lingrui.router.dto.ChatCompletionDTO;
import com.lingrui.router.dto.ChatMessageDTO;
import com.lingrui.router.service.RouterService;
import com.lingrui.router.vo.ChatChoiceVO;
import com.lingrui.router.vo.ChatCompletionVO;
import com.lingrui.router.vo.ChatMessageVO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 路由服务实现类，负责组织请求处理流程
 */
@Service
public class RouterServiceImpl implements RouterService {
    /**
     * 处理非流式聊天请求
     * @param request 调用者传入的聊天请求
     * @return 统一格式的聊天响应
     */
    @Override
    public ChatCompletionVO chat(ChatCompletionDTO request) {
        //创建回复消息，当前使用固定文本验证接口流程
        ChatMessageVO message = new ChatMessageVO();
        message.setRole("assistant");
        message.setContent("这是中转站的测试回复");

        //将回复放入第一个回答选项，标记为正常结束
        ChatChoiceVO choice = new ChatChoiceVO();
        choice.setMessage(message);
        choice.setIndex(0);
        choice.setFinishReason("stop");

        //组装完整的非流式聊天响应
        ChatCompletionVO response = new ChatCompletionVO();
        response.setId("chatcmpl-" + UUID.randomUUID());
        response.setObject("chat.completion");
        response.setCreated(Instant.now().getEpochSecond());
        response.setModel(request.getModel());
        response.setChoices(List.of(choice));
        // TODO: 将固定回复替换为真实上游调用，并补充实际用量
        return response;
    }
}
