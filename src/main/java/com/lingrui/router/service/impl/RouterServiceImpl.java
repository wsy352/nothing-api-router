package com.lingrui.router.service.impl;

import com.lingrui.router.client.UpstreamClient;
import com.lingrui.router.client.impl.DeepseekClientImpl;
import com.lingrui.router.config.ArkProperties;
import com.lingrui.router.config.ChannelProperties;
import com.lingrui.router.dto.ChatCompletionDTO;
import com.lingrui.router.dto.ChatMessageDTO;
import com.lingrui.router.service.RouterService;
import com.lingrui.router.vo.ChatChoiceVO;
import com.lingrui.router.vo.ChatCompletionVO;
import com.lingrui.router.vo.ChatMessageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 路由服务实现类，负责组织请求处理流程
 */
@Service
public class RouterServiceImpl implements RouterService {

    //明确指定使用deepseek 客户端
    @Autowired
    @Qualifier("deepseekClientImpl")
    private UpstreamClient deepseekClient;

    //明确指定使用火山方舟客户端
    @Autowired
    @Qualifier("arkClientImpl")
    private UpstreamClient arkClient;

    //deepseek配置，用于读取deepseek的模型别名
    @Autowired
    private ChannelProperties channelProperties;

    //火山方舟配置，用于读取豆包的模型别名
    @Autowired
    private ArkProperties arkProperties;


    /**
     * 校验请求参数，并选择对应的上游客户端
     * @param request 聊天请求
     * @return 选中的客户端
     */

    private UpstreamClient validateAndSelectClient(ChatCompletionDTO request) {

        //检查模型名称，能不能缺失，也不能是空白字符串
        if(request.getModel() == null || request.getModel().isBlank())
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "model 不能为空"
            );

        //根据模型别名，选择本次请求使用的客户端
        UpstreamClient selectedClient;
        if(request.getModel().equals(channelProperties.getAlias())){
            selectedClient = deepseekClient;
        }else if(request.getModel().equals(arkProperties.getAlias())){
            selectedClient = arkClient;
        }else{
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

        //这里只返回选中的客户端，不发送请求
        return selectedClient;
    }

    /**
     * 处理非流式聊天请求
     * @param request 聊天请求
     * @return 完整聊天响应
     */
    @Override
    public ChatCompletionVO chat(ChatCompletionDTO request) {

        //完成校验，并拿到选中的客户端
        UpstreamClient selectedClient = validateAndSelectClient(request);

        //防止流式请求被误交给非流式方法
        if(Boolean.TRUE.equals(request.getStream())){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "流式请求应通过流式处理入口调用"
            );
        }

        return selectedClient.chat(request);
    }

    /**
     * 处理流式聊天请求
     * @param request 聊天请求
     * @param eventConsumer 收到事件后执行的动作
     */
    @Override
    public void streamChat(ChatCompletionDTO request, Consumer<String> eventConsumer) {

        UpstreamClient selectedClient = validateAndSelectClient(request);

        if(!Boolean.TRUE.equals(request.getStream())){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "流式请求需设置 stream 为 true"
            );
        }


        //把请求和事件处理动作一起交给客户端
        selectedClient.streamChat(request, eventConsumer);
    }




}
