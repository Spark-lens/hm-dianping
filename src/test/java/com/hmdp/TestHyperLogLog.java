package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;

@SpringBootTest
public class TestHyperLogLog {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Test
    void testHyperLogLog(){
        // 准备数据，装用户数据
        String[] users = new String[500];

        // 数组角标
        int index = 0;
        for (int i = 0; i < 10000; i++) {
            // 赋值
            users[index++] = "user_" + i;
            // 每 1000 条发送一次
            if (i % 100 == 0){
                stringRedisTemplate.opsForHyperLogLog().add("hll", Arrays.copyOf(users, index));
                index = 0;
            }
        }
        // 统计数量
        Long size = stringRedisTemplate.opsForHyperLogLog().size("hll");
        System.out.println("数量：" + size);   // 9900
    }
}
