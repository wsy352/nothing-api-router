package com.lingrui.router.client.impl;

import com.lingrui.router.client.UpstreamClient;
import com.lingrui.router.config.ChannelProperties;
import com.lingrui.router.dto.ChatCompletionDTO;
import com.lingrui.router.vo.ChatCompletionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * deepseek客户端，负责与deepseek平台进行http通信
 */
@Component
public class DeepseekClientImpl implements UpstreamClient{

    /**
     * 上游渠道配置，提供请求地址，API Key和模型名
     */
    @Autowired
    private ChannelProperties channelProperties;

    /**
     * HTTP客户端，用于发送HTTP请求
     */
    private final RestClient restClient = RestClient.create();

    /**
     * 调用deepseek的非流式聊天接口
     * @param request 聊天请求参数
     * @return 转换为统一格式的聊天响应
     */
    public ChatCompletionVO chat(ChatCompletionDTO request){
        //组合基础地址和聊天接口路径
        String url = channelProperties.getBaseUrl() + "/chat/completions";

        //组装上游请求体，使用配置中的真实模型名
        Map<String, Object> body = new HashMap<>();
        body.put("model", channelProperties.getModel());
        body.put("messages",request.getMessages());
        body.put("stream", false);

        //当前阶段关闭思考模式，并限制测试调用的输出长度
        Map<String, String> thinking = new HashMap<>();
        thinking.put("type", "disabled");
        body.put("thinking", thinking);
        body.put("max_tokens", 128);

        //发送POST请求，并将相应JSON转换成聊天响应对象
        ChatCompletionVO response = restClient.post()// 创建一个POST请求
                .uri(url)//填写请求地址
                .contentType(MediaType.APPLICATION_JSON)//指定发送内容的格式是JSON
                .header("Authorization", "Bearer " + channelProperties.getApiKey())//填写调用deepseek所需的凭证
                .body(body)//填写要发送的内容
                .retrieve()//准备读取响应
                .body(ChatCompletionVO.class);//执行请求，读取返回的JSON，并转换成Java对象

        return response;
    }
}