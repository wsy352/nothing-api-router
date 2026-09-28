package com.lingrui.router.mapper;

import com.lingrui.router.entity.ChatCallLog;
import com.lingrui.router.vo.ChatCallStatsVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChatCallLogMapper {

    /**
     * 保存一条调用记录，返回插入行数
     */
    int insert(ChatCallLog record);

    /**
     * 查询最近20条调用记录
     */
    List<ChatCallLog> selectRecent();

    ChatCallStatsVO selectStats();
}
