package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

@Slf4j
@Component
public class CacheClient {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 方法一、将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // 方法二、将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));     // 将 Long time, TimeUnit unit 转为秒
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));

    }


    // 方法三、根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,Long time, TimeUnit unit) {

        // 商铺 key
        String key = keyPrefix + id;
        // 1、从 redis 中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2、判断是否存在。是否为空串
        if (StrUtil.isNotBlank(json)) {
            // 3、存在，则返回商城信息
            // json 反序列化 到 Shop对象
            return JSONUtil.toBean(json, type);
        }

        // 缓存是否命中
        // 解决缓存穿透。缓存命中，商铺是否为空值
        if (json != null) {
            // ？？？
            return null;
        }

        // 4、不存在，根据 id 查询数据库
        R r = dbFallback.apply(id);
        log.info("根据 id 查询数据库成功！");

        // 5、不存在，返回 空值
        if (r == null) {
            // 解决缓存穿透。将空值写入 redis
            set(key,"",time,unit);
            // 返回错误信息
            return null;
        }

        // 6、存在，写入 redis
        // JSONUtil.toJsonStr(r)。将 对象 序列化为 Json
        set(key,JSONUtil.toJsonStr(r),time,unit);

        // 7、返回
        return r;
    }


    // 方法四、根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
    // 缓存击穿 - 逻辑过期
    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time,TimeUnit unit) {
        // 商铺 key
        String key = keyPrefix + id;
        // 1、从 redis 中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2、判断是否存在。是否为空串
        if (StrUtil.isBlank(json)) {
            // 2、存在，返回空
            return null;
        }

        // 3、缓存命中。需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 4、判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 4.1、未过期，直接返回店铺信息
            return r;
        }
        // 4.2、已过期。需要重建缓存
        // 5、重建缓存
        // 5.1、获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean lockStatus = tryLock(lockKey);
        // 5.2、判断锁是否获取成功
        if (lockStatus) {
            // 5.3、锁获取成功。开启独立线程，实现缓存重建。
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                // 重建缓存
                try {
                    // 查数据库
                    R r1 = dbFallback.apply(id);
                    // 存入缓存
                    setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
            });
        }
        // 7、返回过期的商品数据
        return r;
    }


    // 方法五、根据指定的key查询缓存，并反序列化为指定类型，需要利用 返回空值 + 互斥锁 解决缓存击穿问题
    public <R,ID> R queryWithMutex(String keyPrefix ,ID id,Class<R> type,Function<ID,R> dbFallback,Long time, TimeUnit unit) {
        // 商铺 key
        String key = keyPrefix + id;
        // 1、从 redis 中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2、判断是否存在。是否为空串
        if (StrUtil.isNotBlank(json)) {
            // 3、存在，则返回商城信息
            // json 反序列化 到 对象
            return JSONUtil.toBean(json, type);
        }

        // 缓存是否命中
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        R r = null;
        try {
            // 解决缓存穿透。缓存命中，商铺是否为空值
            if (json != null) {
                // ？？？
                return null;
            }
            // 4、实现缓存重构
            // 4.1、获取互斥锁
            boolean lockStatus = tryLock(lockKey);
            // 4.2、判断锁是否获取成功
            if (!lockStatus) {
                // 4.3、获取锁失败，休眠一段时间
                Thread.sleep(50);
                return queryWithMutex(keyPrefix ,id,type,dbFallback,time,unit);
            }
            // 4.4、获取锁成功，根据 id 查询数据库
            r = dbFallback.apply(id);
            Thread.sleep(200);      // 休眠方便测试，模拟重建的延时
            log.info("根据 id 查询数据库成功！");

            // 4.5、将商铺数据写入 redis
            if (r == null) {
                // 解决缓存穿透。将空值写入 redis
                stringRedisTemplate.opsForValue().set(key, "", time,unit);
                // 返回错误信息
                return null;
            }
            // 6、存在，写入 redis
            // 将 对象 序列化为 Json，添加过期时间：30分钟
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time,unit);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockKey);
        }
        // 7、返回
        return r;
    }


    // 互斥锁 - 加锁
    private boolean tryLock(String lockKey) {
        // 设置锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        // 返回是否加锁成功
        return BooleanUtil.isTrue(flag);
    }

    // 互斥锁 - 解锁
    private void unLock(String lockKey) {
        // 删除锁
        stringRedisTemplate.delete(lockKey);
    }

}
