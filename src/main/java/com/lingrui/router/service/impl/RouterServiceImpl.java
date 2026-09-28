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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

    //负责解析JSON，用来操作JSON
    @Autowired
    private ObjectMapper objectMapper;


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

        ChatCallLog record = createCallLog(request, selectedClient);
        //用于计算上游调用和响应处理的耗时
        long startNanos = System.nanoTime();

        try {
            //调用真实上游
            ChatCompletionVO response = selectedClient.chat(request);

            if(response == null){
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
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
            saveCallLog(record, startNanos);
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

        //为整次流式请求创建一条记录
        ChatCallLog record = createCallLog(request, selectedClient);
        long startNanos = System.nanoTime();

        try{
            selectedClient.streamChat(request, data -> {

                //收到事件后，先检查里面有没有用量
                collectStreamUsage(data, record);

                //再把这条事件原样交给 Controller 发送
                eventConsumer.accept(data);
            });

            //当前客户端只有收到并转发[DONE]后才正常返回
            record.setStatus("SUCCESS");
        } catch (RuntimeException exception){
            record.setStatus("FAILED");
            record.setErrorCode("stream_failed");

            //继续交给 Controller 处理错误响应
            throw exception;
        } finally{
            //整次调用结束后保存一次，不是每条事件保存一次
            saveCallLog(record, startNanos);
        }
    }

    /**
     * 创建调用记录，填写调用前已经知道的信息
     */
    private ChatCallLog createCallLog(ChatCompletionDTO request, UpstreamClient selectedClient){
        ChatCallLog record = new ChatCallLog();

        record.setRequestId(UUID.randomUUID().toString());
        record.setModelAlias(request.getModel());
        record.setChannel(selectedClient == arkClient ? "ark" : "deepseek");
        record.setStream(Boolean.TRUE.equals(request.getStream()));
        record.setCreatedAt(LocalDateTime.now());

        return record;
    }

    /**
     * 计算耗时并尝试保存记录，保存失败时记录日志
     */
    private void saveCallLog(ChatCallLog record, long startNanos){
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        record.setDurationMs(durationMs);

        try {
            chatCallLogMapper.insert(record);
        } catch(RuntimeException saveException){
            log.error(
                    "保存调用记录失败,requestId = {}",
                    record.getRequestId(),
                    saveException
            );
        }
    }

    /**
     * 从一条流式事件中读取流量，写入当前调用记录
     */
    private void collectStreamUsage(String data, ChatCallLog record){

        //结束标记不是 JSON ，不需要解析
        if("[DONE]".equals(data)) {
            return;
        }

        try {
            //将 JSON 字符串解析成可以按字段访问的结构
            JsonNode root = objectMapper.readTree(data);

            if(root == null){
                return;
            }

            //上游可能在流内部返回错误
            if(root.hasNonNull("error")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "上游返回了流式错误"
                );
            }

            JsonNode usageNode = root.get("usage");

            //这条事假没有用量，继续等待后面的事件
            if(usageNode == null || usageNode.isNull()){
                return;
            }

            //将 usage 这一部分转换成已有的 UsageVO
            UsageVO usage = objectMapper.treeToValue(usageNode, UsageVO.class);

            record.setPromptTokens(usage.getPromptTokens());
            record.setCompletionTokens(usage.getCompletionTokens());
            record.setTotalTokens(usage.getTotalTokens());
        } catch (JacksonException exception){
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "上游流式数据格式异常",
                    exception
            );
        }

    }





}
