package com.lingrui.router.service.impl;

import com.lingrui.router.client.UpstreamClient;
import com.lingrui.router.client.impl.DeepseekClientImpl;
import com.lingrui.router.config.ChannelProperties;
import com.lingrui.router.dto.ChatCompletionDTO;
import com.lingrui.router.dto.ChatMessageDTO;
import com.lingrui.router.service.RouterService;
import com.lingrui.router.vo.ChatChoiceVO;
import com.lingrui.router.vo.ChatCompletionVO;
import com.lingrui.router.vo.ChatMessageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    //当前渠道配置，用于读取允许调用的模型别名
    @Autowired
    private ChannelProperties channelProperties;


    /**
     * 处理非流式聊天请求
     * @param request 调用者传入的聊天请求
     * @return 统一格式的聊天响应
     */
    @Override
    public ChatCompletionVO chat(ChatCompletionDTO request) {

        //检查模型名称，能不能缺失，也不能是空白字符串
        if(request.getModel() == null || request.getModel().isBlank())
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "model 不能为空"
            );

        //调用者填写的模型名必须与配置中的别名一致
        if(!request.getModel().equals(channelProperties.getAlias())){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "不支持的模型别名：" + request.getModel()
            );
        }

        //消息列表必须存在，并且不能是空白字符串
        if(request.getMessages() == null || request.getMessages().isEmpty()){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "messages 至少需要一条消息"
            );
        }

        //逐条检查消息，当前只支持system,user,assistant三种文本消息
        for (ChatMessageDTO message : request.getMessages()){
            //列表元素本身不能是null
            if(message == null){
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "messages 中不能包含null 消息"
                );
            }

            //消息角色必须填写
            String role = message.getRole();
            if(role == null || role.isBlank()){
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "messages 中的 role 不能为空"
                );
            }

            //当前阶段只接受这三种角色
            if(!"system".equals(role)
                    && !"user".equals(role)
                    && !"assistant".equals(role)){
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "目前 messages 中的 role 只能是 system, user, assistant"
                );
            }

            //当前文本聊天版本要求每条消息都有非空白内容
            String content = message.getContent();
            if(content == null || content.isBlank()){
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "messages 中的 content 不能为空"
                );
            }
        }

        //当前仅支持非流式调用，明确拒绝流式请求
        if(Boolean.TRUE.equals(request.getStream())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "当前尚不支持流式调用"
            );
        }
        //通过检查后，才能调用上游平台
        //调用客户端，由客户端向deepseek发送请求并读取回答
        ChatCompletionVO response = upstreamClient.chat(request);
        return response;
    }
}
