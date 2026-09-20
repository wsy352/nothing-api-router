package com.lingrui.router.client.impl;

import com.lingrui.router.client.UpstreamClient;
import com.lingrui.router.config.ArkProperties;
import com.lingrui.router.dto.ChatCompletionDTO;
import com.lingrui.router.vo.ChatCompletionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * 火山方舟客户端，负责向方舟发送聊天请求
 */
@Component
public class ArkClientImpl implements UpstreamClient {

    //读取router.ark下的配置
    @Autowired
    private ArkProperties arkProperties;

    //用于发送HTTP请求
    private final RestClient restClient = RestClient.create();

    /**
     * 调用方舟火山的非流式聊天接口
     * @param request 调用者传入的聊天请求
     * @return 转换后的聊天响应
     */
    @Override
    public ChatCompletionVO chat(ChatCompletionDTO request) {
        //拼出刚才在ApiFox中测试成功的地址
        String url = arkProperties.getBaseUrl() + "/chat/completions";

        //对应ApiFox中Body -> JSON的内容
        Map<String, Object> body = new HashMap<>();
        body.put("model", arkProperties.getModel());
        body.put("messages", request.getMessages());
        body.put("stream", false);

        //发送请求 ，再把响应 JSON 转换成 Java 对象
        ChatCompletionVO response = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + arkProperties.getApiKey())
                .body(body)
                .retrieve()
                .body(ChatCompletionVO.class);

        return response;

    }

}
