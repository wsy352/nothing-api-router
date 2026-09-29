package com.lingrui.router.controller;

import com.lingrui.router.entity.ChatCallLog;
import com.lingrui.router.mapper.ChatCallLogMapper;
import com.lingrui.router.service.RouterAuthService;
import com.lingrui.router.vo.ChatCallStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 查询调用记录，当前用于本地测试
 */
@RestController
@RequestMapping("/api/call-logs")
public class ChatCallLogController {

    @Autowired
    private ChatCallLogMapper chatCallLogMapper;

    @Autowired
    private RouterAuthService routerAuthService;

    /**
     * 查询最近20条调用记录
     */
    @GetMapping
    public List<ChatCallLog> listRecent(@RequestHeader(value = "Authorization", required = false) String authorization) {

        routerAuthService.validateAdminKey(authorization);

        return chatCallLogMapper.selectRecent();
    }

    /**
     * 查询全部调用记录的数量统计
     */
    @GetMapping("/stats")
    public ChatCallStatsVO getStats(@RequestHeader(value = "Authorization", required = false) String authorization){

        routerAuthService.validateAdminKey(authorization);

        return chatCallLogMapper.selectStats();
    }
}
