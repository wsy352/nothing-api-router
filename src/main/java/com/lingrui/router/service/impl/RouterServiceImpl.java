package com.lingrui.router.service.impl;

import com.lingrui.router.client.UpstreamClient;
import com.lingrui.router.client.impl.DeepseekClientImpl;
import com.lingrui.router.config.ArkProperties;
import com.lingrui.router.config.ChannelProperties;
import com.lingrui.router.config.ModelPriceProperties;
import com.lingrui.router.context.RouterAuthContext;
import com.lingrui.router.dto.ChatCompletionDTO;
import com.lingrui.router.dto.ChatMessageDTO;
import com.lingrui.router.entity.ChatCallLog;
import com.lingrui.router.model.NormalizedUsage;
import com.lingrui.router.service.ChatBillingService;
import com.lingrui.router.service.RouterService;
import com.lingrui.router.service.UsageNormalizationService;
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
    private ChatBillingService chatBillingService;

    //负责解析JSON，用来操作JSON
    @Autowired
    private ObjectMapper objectMapper;

    //用量归一化服务：统一不同平台的 token 字段，并检查用量是否合理
    @Autowired
    private UsageNormalizationService usageNormalizationService;


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

        //非流式与流式共用校验接口，统一补齐输入长度
        normalizeMaxTokens(request, selectedClient);

        return selectedClient;
    }

    /**
     * 处理非流式聊天请求
     * @param request 聊天请求
     * @param routerAuthContext 服务端验证得到的调用者身份
     * @return 完整聊天响应
     */
    @Override
    public ChatCompletionVO chat(ChatCompletionDTO request, RouterAuthContext routerAuthContext) {

        //完成校验，并拿到选中的客户端
        UpstreamClient selectedClient = validateAndSelectClient(request);

        //防止流式请求被误交给非流式方法
        if(Boolean.TRUE.equals(request.getStream())){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "流式请求应通过流式处理入口调用"
            );
        }

        ChatCallLog record = createCallLog(request, selectedClient, routerAuthContext);
        //用于计算上游调用和响应处理的耗时
        long startNanos = System.nanoTime();

        //预留失败直接结束请求，避免余额不足时仍消耗上游 token
        chatBillingService.reserveAndCreate(record);

        try {
            //调用真实上游
            ChatCompletionVO response = selectedClient.chat(request);

            if(response == null){
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "上游返回了空响应"
                );
            }

            //将上游用量转换成统一格式，再写入调用记录
            fillNormalizedUsage(response.getUsage(), record);

                record.setStatus("SUCCESS");
                return response;

            }catch (RuntimeException exception){
                record.setStatus("FAILED");
                record.setErrorCode("upstream_call_failed");

                //保留原来的异常处理流程
                throw exception;

            } finally {
            //无论成功还是失败，都尝试保存调用记录
            finishBilling(record, startNanos);
        }
    }

    /**
     * 处理流式聊天请求
     * @param request 聊天请求
     * @param routerAuthContext 服务端验证得到的调用者身份
     * @param eventConsumer 收到事件后执行的动作
     */
    @Override
    public void streamChat(ChatCompletionDTO request, RouterAuthContext routerAuthContext, Consumer<String> eventConsumer) {

        UpstreamClient selectedClient = validateAndSelectClient(request);

        if(!Boolean.TRUE.equals(request.getStream())){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "流式请求需设置 stream 为 true"
            );
        }

        //为整次流式请求创建一条记录
        ChatCallLog record = createCallLog(request, selectedClient, routerAuthContext);
        long startNanos = System.nanoTime();

        //预留资金和创建记录同时提交，失败时不调用上游
        chatBillingService.reserveAndCreate(record);

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
            //整次流式调用结束后，统一结算或保存待处理结果
            finishBilling(record, startNanos);
        }
    }

    /**
     * 创建调用记录，填写调用前已经知道的信息
     */
    private ChatCallLog createCallLog(ChatCompletionDTO request, UpstreamClient selectedClient, RouterAuthContext routerAuthContext){
        ChatCallLog record = new ChatCallLog();

        record.setRequestId(UUID.randomUUID().toString());
        record.setUserId(routerAuthContext.getUserId());
        record.setApiKeyId(routerAuthContext.getApiKeyId());
        record.setModelAlias(request.getModel());
        record.setChannel(selectedClient == arkClient ? "ark" : "deepseek");
        record.setStream(Boolean.TRUE.equals(request.getStream()));
        record.setCreatedAt(LocalDateTime.now());

        //调用上游之前，确定并保存本次使用的价格
        fillPriceSnapshot(selectedClient, record);

        //在调用开始前确定本次预算，后续资金操作使用这一个值
        record.setReservedUnits(resolveReserveUnits(selectedClient));

        return record;
    }

    /**
     * 完成调用后的资金处理
     * 成功且用量完整时尝试结算，其他情况保存为待处理
     */
    private void finishBilling(ChatCallLog chatCallLog, long startNanos){

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

        chatCallLog.setDurationMs(durationMs);

        boolean completeUsage = chatCallLog.getPromptTokens() != null
                && chatCallLog.getCachedInputTokens() != null
                && chatCallLog.getCompletionTokens() != null;

        if("SUCCESS".equals(chatCallLog.getStatus()) && completeUsage){
            try {

                //通过另一个 Spring 服务执行短事务
                chatBillingService.settleAndUpdate(chatCallLog);

                return;
            } catch(RuntimeException settlementException){

                //此调用返回异常时，结算事务已经结束
                log.error(
                        "聊天结算失败，将尝试保存待处理结果，requestId={}",
                        chatCallLog.getRequestId(),
                        settlementException
                );
            }
        }

        try {
            //使用独立的一次事务调用保存结果，不存在失败的结算事务中继续写入
            chatBillingService.markPending(chatCallLog);
        } catch(RuntimeException pendingException){

            //不覆盖原来的上游异常，也不向已经结束的 SSE 响应追加错误
            log.error(
                    "保存待处理结果失败，需要核对预留记录，requestId={}",
                    chatCallLog.getRequestId(),
                    pendingException
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

            //把当前事件中的原始用量转换成 java 对象
            UsageVO usageVO = objectMapper.treeToValue(usageNode, UsageVO.class);

            //与非流式响应使用相同的归一化规则
            fillNormalizedUsage(usageVO, record);

        } catch (JacksonException exception){
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "上游流式数据格式异常",
                    exception
            );
        }

    }

    /**
     * 统一上游用量，并填写当前调用记录
     * @param usageVO 上游返回的原始用量，允许为空
     * @param chatCallLog 当前请求的调用记录，包含实际使用的渠道
     */
    private void fillNormalizedUsage(
            UsageVO usageVO,
            ChatCallLog chatCallLog
    ){
        NormalizedUsage normalizedUsage;

        try{
            //按当前渠道解释上游字段，得到项目内部的统一用量
            normalizedUsage = usageNormalizationService.normalize(chatCallLog.getChannel(), usageVO);
        } catch(IllegalArgumentException | ArithmeticException exception){
            //数据来自上游；用量矛盾或数值溢出属于上游响应异常
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "上游返回的用量数据异常",
                    exception
            );
        }

        //保存统一后的用量。未知用量继续保留 null， 不把缺失数据当做零用量
        chatCallLog.setPromptTokens(normalizedUsage.getPromptTokens());
        chatCallLog.setCachedInputTokens(normalizedUsage.getCachedInputTokens());
        chatCallLog.setCompletionTokens(normalizedUsage.getCompletionTokens());
        chatCallLog.setTotalTokens(normalizedUsage.getTotalTokens());
    }

    /**
     * 保存本次调用使用的价格快照
     * @param selectedClient 本次选中的上游客户端
     * @param chatCallLog 当前请求的调用记录
     */
    private void fillPriceSnapshot(
            UpstreamClient selectedClient,
            ChatCallLog chatCallLog
    ){
        ModelPriceProperties modelPriceProperties;

        //价格必须来自实际选中的渠道，不能固定使用某个平台的价格
        if(selectedClient == deepseekClient){
            modelPriceProperties = channelProperties.getPrice();
        } else if (selectedClient == arkClient){
            modelPriceProperties = arkProperties.getPrice();
        }else {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "未找到当前渠道的价格配置"
            );
        }

        if(modelPriceProperties == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "未找到当前渠道的价格配置"
            );
        }

        //复制具体数值，让本次记录保留调用时的价格
        chatCallLog.setPriceVersion(modelPriceProperties.getVersion());
        chatCallLog.setInputUnitsPerMillion(modelPriceProperties.getInputUnitsPerMillion());
        chatCallLog.setCachedInputUnitsPerMillion(modelPriceProperties.getCachedInputUnitsPerMillion());
        chatCallLog.setOutputUnitsPerMillion(modelPriceProperties.getOutputUnitsPerMillion());

        //在发送上游请求前检查快照，避免消耗 token 后才发现价格不可用
        String priceVersion = chatCallLog.getPriceVersion();

        if(priceVersion == null || priceVersion.isBlank() || priceVersion.length() > 64){
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "价格版本不能为空，且长度不能超过64个字符"
            );
        }

        validateSnapshotPrice(chatCallLog.getInputUnitsPerMillion());
        validateSnapshotPrice(chatCallLog.getCachedInputUnitsPerMillion());
        validateSnapshotPrice(chatCallLog.getOutputUnitsPerMillion());
    }

    /**
     * 检查快照中的单价，允许明确配置零价格，拒绝缺失或负价格
     */
    private void validateSnapshotPrice(Long unitPrice) {
        if (unitPrice == null || unitPrice < 0) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "渠道单价不能为空或为负数"
            );
        }
    }

    /**
     * 补齐并校验回答长度，让预算计算与上游调用使用同一个值
     * @param request 聊天请求，校验后保存实际使用的 maxTokens
     * @param selectedClient 选中的上游客户端
     */
    private void normalizeMaxTokens(ChatCompletionDTO request, UpstreamClient selectedClient){

        Integer defaultMaxTokens;
        Integer maxAllowedTokens;

        if(selectedClient == deepseekClient){
            defaultMaxTokens = channelProperties.getDefaultMaxTokens();
            maxAllowedTokens = channelProperties.getMaxAllowedTokens();
        } else if (selectedClient == arkClient){
            defaultMaxTokens = arkProperties.getDefaultMaxTokens();
            maxAllowedTokens = arkProperties.getMaxAllowedTokens();
        } else {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "未找到当前渠道的输出限制配置"
            );
        }

        //配置错误需要服务端修正，不能归责于调用者
        if(defaultMaxTokens == null || maxAllowedTokens == null || defaultMaxTokens <= 0 || maxAllowedTokens <= 0 || defaultMaxTokens > maxAllowedTokens){
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "渠道的输出长度配置不合法"
            );
        }

        Integer maxTokens = request.getMaxTokens();

        //没填写时补默认值；明确填写或零或负数时不能偷偷替换
        if(maxTokens == null){
            maxTokens = defaultMaxTokens;
        }

        if(maxTokens <= 0 || maxTokens > maxAllowedTokens){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "max_tokens必须在 1 到" + maxAllowedTokens + "之间"
            );
        }

        request.setMaxTokens(maxTokens);
    }

    /**
     * 读取本次调用请求的预留预算
     * 当前采用渠道固定预算，后续可以替换为按请求估算的预算
     * @param selectedClient 本次选中的上游客户端
     * @return 计划预留的金额，单位微元
     */
    private long resolveReserveUnits(UpstreamClient selectedClient){
        Long reserveUnits;

        if(selectedClient == deepseekClient){
            reserveUnits = channelProperties.getReserveUnits();
        } else if (selectedClient == arkClient){
            reserveUnits = arkProperties.getReserveUnits();
        } else {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "未找到当前渠道的预算配置"
            );
        }

        //钱包预留方法要求正整数；配置错误应在调用上游之前发现
        if(reserveUnits == null || reserveUnits <= 0){
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "渠道的预算配置不合法"
            );
        }

        return reserveUnits;
    }

}
