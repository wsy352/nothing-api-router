package com.lingrui.router.controller;

import com.lingrui.router.dto.ChatCompletionDTO;
import com.lingrui.router.service.RouterService;
import com.lingrui.router.vo.ChatCompletionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *聊天接口，负责接收调用者的聊天请求
 */
@RestController
@RequestMapping("/v1")
public class ChatCompletionController {
    @Autowired
    private RouterService routerService;
    /**
     * 处理非流式聊天请求
     * @param request 调用者传入的聊天请求
     * @return 聊天响应，包含模型回答和用量信息
     */
    @PostMapping("/chat/completions")
    public ChatCompletionVO chat(@RequestBody ChatCompletionDTO request){
        //TODO 校验请求参数，当前阶段只实现非流式请求
        //TODO 调用RouterService处理请求，获得聊天响应。
        return routerService.chat(request);
    }
}
