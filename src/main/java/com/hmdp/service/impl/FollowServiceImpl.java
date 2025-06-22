package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    /**
     * 关注用户
     * @param followUserId    关注用户的 id
     * @param isFollow        true 关注 / false取关
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1、获取登录用户
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FOLLOW_KEY + userId;    // 该用户 存放 关注用户的 redis key

        // 2、关注/取关
        if (isFollow){
            // 2.1、关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess){
                // 把关注用户的id，放入redis的set集合 sadd userId followerUserId
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else {
            // 2.2、取关
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (isSuccess){
                // 把关注用户的id从Redis集合中移除
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }


    /**
     * 是否关注用户
     * @param followUserId  关注用户的 id
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        // 1、获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2、查询是否存在关注数据
        Integer count = query()
                .eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .count();
        return Result.ok(count > 0);
    }

    /**
     * 共同关注
     * @param userId 需要进行计算是否有共同关注的 用户id
     * @return
     */
    @Override
    public Result followCommons(Long userId) {
        // 1、获取当前用户
        Long currentId = UserHolder.getUser().getId();

        // 2、当前用户 存放 关注用户的 redis key
        String currentKey = RedisConstants.FOLLOW_KEY + currentId;
        // 3、需要进行计算是否有共同关注的 用户 存放 关注用户的 redis key
        String userIdKey = RedisConstants.FOLLOW_KEY + userId;

        // 4、计算关注用户的交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(currentKey, userIdKey);
        if (intersect == null || intersect.isEmpty()){
            // 无交集
            return Result.ok(Collections.emptyList());
        }
        // 5、解析 id 集合
        List<Long> ids = intersect
                .stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());

        // 5、查询用户
        List<UserDTO> users = userService
                .listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        // 6、返回
        return Result.ok(users);
    }
}
