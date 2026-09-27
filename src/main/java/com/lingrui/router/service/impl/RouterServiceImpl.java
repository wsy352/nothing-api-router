package com.lingrui.router.service.impl;

import com.lingrui.router.client.UpstreamClient;
import com.lingrui.router.client.impl.DeepseekClientImpl;
import com.lingrui.router.config.ArkProperties;
import com.lingrui.router.config.ChannelProperties;
import com.lingrui.router.dto.ChatCompletionDTO;
import com.lingrui.router.dto.ChatMessageDTO;
import com.lingrui.router.entity.ChatCallLog;
import com.lingrui.router.mapper.ChatCallLogMapper;
import com.lingrui.router.service.RouterService;
import com.lingrui.router.vo.ChatChoiceVO;
import com.lingrui.router.vo.ChatCompletionVO;
import com.lingrui.router.vo.ChatMessageVO;
import com.lingrui.router.vo.UsageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 路由服务实现类，负责组织请求处理流程
 */
@Slf4j
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

    @Autowired
    private ChatCallLogMapper chatCallLogMapper;


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

        ChatCallLog record = new ChatCallLog();
        record.setRequestId(UUID.randomUUID().toString());
        record.setModelAlias(request.getModel());
        record.setChannel(selectedClient == arkClient ? "ark" : "deepseek");
        record.setStream(false);
        record.setCreatedAt(LocalDateTime.now());

        //用于计算上游调用和响应处理的耗时
        long startNanos = System.nanoTime();

        try {
            //调用真实上游
            ChatCompletionVO response = selectedClient.chat(request);

            if(response == null){
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "上游返回了空响应"
                );
            }

            //只有上游实际返回用量，才填写Token数
            UsageVO usage = response.getUsage();
            if(usage != null) {
                record.setPromptTokens(usage.getPromptTokens());
                record.setCompletionTokens(usage.getCompletionTokens());
                record.setTotalTokens(usage.getTotalTokens());
            }

                record.setStatus("SUCCESS");
                return response;

            }catch (RuntimeException exception){
                record.setStatus("FAILED");
                record.setErrorCode("upstream_call_failed");

                //保留原来的异常处理流程
                throw exception;

            } finally {
            //无论成功还是失败，都尝试保存调用记录
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            record.setDurationMs(durationMs);

            try {
                chatCallLogMapper.insert(record);
        } catch (RuntimeException saveException){
                //保存记录失败时留下日志，不覆盖原来的调用结果
                log.error(
                        "保存调用记录失败,requestId = {}",
                        record.getRequestId(),
                        saveException
                );
            }
        }
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
