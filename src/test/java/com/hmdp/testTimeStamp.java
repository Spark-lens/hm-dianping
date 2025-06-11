package com.hmdp;

import com.hmdp.utils.RedisWorker;
import net.sf.jsqlparser.expression.TimestampValue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
public class testTimeStamp {

    // 2025年6月11日 凌晨0点0分0秒 对应的时间戳 1746921600
    private static final long BEGIN_TIMESTAMP = 1746921600;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Autowired
    private RedisWorker redisWorker;

    /**
     * 测试生成 32 位时间戳
     * @return
     */
    @Test
    public void testTimeStamp1(){
//        LocalDateTime localDateTime = LocalDateTime.of(2025, 5, 11, 0, 0, 0);
//        long second = localDateTime.toEpochSecond(ZoneOffset.UTC);
//        System.out.println("时间戳" + second);     // 1746921600
        LocalDateTime nowTime = LocalDateTime.now();
        long nowSecond = nowTime.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        System.out.println(timestamp);

        LocalDateTime nowDateTime = LocalDateTime.now();
        String date = nowDateTime.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        System.out.println(date);
    }

    @Test
    public void testRedisWorker() throws InterruptedException {

        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i <100; i++) {
                Long id = redisWorker.nextId("order");
                System.out.println("id：" + id);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time：" + (end - begin));
    }

}
