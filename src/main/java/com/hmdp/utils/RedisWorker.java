package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisWorker {

    /**
     * 开始时间戳
     * 2025年6月11日 凌晨0点0分0秒 对应的时间戳 1746921600
     */
    private static final long BEGIN_TIMESTAMP = 1746921600;

    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * redis 实现唯一id
     * @param keyPrefix
     * @return
     */
    public Long nextId(String keyPrefix){
        // 1、生成时间戳
        LocalDateTime nowDateTime = LocalDateTime.now();        // 2025年6月11日 23:47:00
        long nowSecond = nowDateTime.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2、生成序列号
        // 2.1、获取当前日期，精确到天
        // yyyy:MM:dd 是为了 存入 redis 方便分组
        String date = nowDateTime.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));    // 2025:6:11
        // 2.2、自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);      // increment() 返回值从 0 开始计算

        // 3、拼接 id，并返回
        return timestamp << COUNT_BITS | count;
    }

}
