package com.lingrui.router.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UsageVO {

    //输入token数，包含本次请求携带的上下文
    @JsonProperty("prompt_tokens")
    private Long promptTokens;

    //模型生成的token数
    @JsonProperty("completion_tokens")
    private Long completionTokens;

    //输入与输出的总token数
    @JsonProperty("total_tokens")
    private Long totalTokens;

}
