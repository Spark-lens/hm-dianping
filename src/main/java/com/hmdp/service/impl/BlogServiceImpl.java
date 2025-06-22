package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Autowired
    private IFollowService followService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询热点 博客
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户、查询blog是否被用户点过赞
//        records.forEach(this::queryBlogUser);
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 查询博客信息
     * @param id 博客id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        // 1、查询博客
        Blog blog = getById(id);
        // 2、判断博客是否为空
        if (blog == null){
            return Result.fail("查询不到博客信息...");
        }
        // 3、设置博客的用户姓名、用户图标
        queryBlogUser(blog);
        // 4、查询blog是否被用户点过赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 查询blog是否被用户点过赞
     * @param blog
     */
    private void isBlogLiked(Blog blog) {
        // 1、获取点赞用户
        UserDTO user = UserHolder.getUser();
        if (user == null){
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();
        // 2、从 redis 获取该博客的用户点赞信息 true 点过赞、false 未点过赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key,userId.toString());
        blog.setIsLike(score != null);
    }

    /**
     * 根据blog设置相关的用户姓名、用户图标
     * @param blog
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 用户点赞
     * @param id 博客id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        // 1、获取点赞用户
        Long userId = UserHolder.getUser().getId();

        // 2、从 redis 获取该博客的用户点赞信息 true 点过赞、false 未点过赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key,userId.toString());

        // 3、判断用户是否点过赞
        if (score == null){
            // 4、未点过赞，点赞数 +1
            // 4.1、数据库点赞数 +1        update tb_blog liked = liked + 1 where id = id
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess){
                // 4.2、redis 增加该用户id、score
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else {
            // 5、已点过赞，点赞数 -1
            // 5.1、数据库点赞数 -1        update tb_blog liked = liked - 1 where id = id
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess){
                // 5.2、redis 删去该用户id
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }

/*    @Override
    // redis set实现用户只能点一次赞
    public Result likeBlog(Long id) {
        // 1、获取点赞用户
        Long userId = UserHolder.getUser().getId();

        // 2、从 redis 获取该博客的用户点赞信息 true 点过赞、false 未点过赞
        String key = BLOG_LIKED_KEY + id;
        Boolean isLike = stringRedisTemplate.opsForSet().isMember(key, userId.toString());

        // 3、判断用户是否点过赞
        if (Boolean.FALSE.equals(isLike)){
            // 4、未点过赞，点赞数 +1
            // 4.1、数据库点赞数 +1        update tb_blog liked = liked + 1 where id = id
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess){
                // 4.2、redis 增加该用户id
                stringRedisTemplate.opsForSet().add(key, String.valueOf(userId));
            }
        }else {
            // 5、已点过赞，点赞数 -1
            // 5.1、数据库点赞数 -1        update tb_blog liked = liked - 1 where id = id
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess){
                // 5.2、redis 删去该用户id
                stringRedisTemplate.opsForSet().remove(key, String.valueOf(userId));
            }
        }
        return Result.ok();
    }*/


    /**
     * 查询博客最先点赞的五个用户
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        // 1、查询 top5 的点赞用户 zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }

        // 2、解析出其中的用户 id
        List<Long> ids = top5.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        String idsStr = StrUtil.join(",", ids);

        // 3、根据用户 id 查询用户 where id in （5,1） order by field (id , 5 , 1)
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("order by field (id," + idsStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        // 4、返回
        return Result.ok(userDTOS);
    }


    /**
     * 根据用户id查询博主的探店笔记
     * @param userId    用户id
     * @param current
     * @return
     */
    @Override
    public Result queryBlogByUserId(Long userId, Integer current) {
        // 1、获取用户的博客数据
        Page<Blog> page = query()
                .eq("user_id", userId)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 2、获取当前业的数据
        List<Blog> records = page.getRecords();
        // 3、返回
        return Result.ok(records);
    }

    /**
     * 保存博客
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 1、获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2、保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess){
            return Result.fail("博客保存失败！");
        }

        // 3、查询笔记作者的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();

        // 4、推送笔记 id 给所有粉丝
        for (Follow follow : follows) {
            // 4.1、获取粉丝 id
            Long userId = follow.getUserId();

            // 4.2、推送
            String key = RedisConstants.FEED_KEY + userId;
            stringRedisTemplate.opsForZSet()
                    .add(key,blog.getId().toString(),System.currentTimeMillis());
        }

        // 5、返回id
        return Result.ok(blog.getId());
    }

    /**
     * 实现分页查询收邮箱
     * @param max 上一次查询的最小时间戳
     * @param offset 偏移量
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1、获取当前用户
        Long userId = UserHolder.getUser().getId();

        // 2、查询收件箱 ZREVRANGEBYSCORE key Max Min LIMIT offset count
        String key = RedisConstants.FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);

        // 3、非空判断
        if (typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }

        // 4、解析数据：
        ArrayList<Object> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            // 4.1、获取博客id
            ids.add(Long.valueOf(tuple.getValue()));

            // 4.2、获取分数（时间戳）
            long time = tuple.getScore().longValue();
            if (time == minTime){
                os++;
            }else {
                minTime = time;
                os = 1;
            }
        }
        os = minTime == max ? os : os + offset;

        // 5、根据 id 查询 blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query()
                .in("id", ids)
                .last("order by field(id, " + idStr + ")").list();

        for (Blog blog : blogs) {
            // 5.1、查询blog有关的用户
            queryBlogUser(blog);
            // 5.2、查询blog是否被点赞
            isBlogLiked(blog);
        }

        // 6、封装并返回
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(offset);
        r.setMinTime(minTime);

        return Result.ok(r);
    }
}
