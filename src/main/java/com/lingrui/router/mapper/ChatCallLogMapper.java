package com.lingrui.router.mapper;

import com.lingrui.router.entity.ChatCallLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatCallLogMapper {

    /**
     * 保存一条调用记录，返回插入行数
     */
    int insert(ChatCallLog record);
}
