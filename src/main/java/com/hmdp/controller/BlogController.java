package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;


    /**
     * 保存博客
     * @param blog
     * @return
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 用户点赞
     * @param id 博客id
     * @return
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 查询热点 博客
     * @param current
     * @return
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    /**
     * 查询博客信息
     * @param id 博客id
     * @return
     */
    @GetMapping("/{id}")
    public Result queryBlogByById(@PathVariable Long id){
        return blogService.queryBlogById(id);
    }

    /**
     * 查询博客最先点赞的五个用户
     * @param id 博客id
     * @return
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable Long id){
        return blogService.queryBlogLikes(id);
    }


    /**
     * 根据用户id查询博主的探店笔记
     * @param userId    用户id
     * @param current
     * @return
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(@RequestParam("id") Long userId,
                                    @RequestParam(value = "current",defaultValue = "1") Integer current){
        return blogService.queryBlogByUserId(userId,current);
    }

    /**
     * 实现分页查询收邮箱
     * @param max 上一次查询的最小时间戳
     * @param offset 偏移量
     * @return
     */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(@RequestParam("lastId") Long max,
                                    @RequestParam(value = "offset",defaultValue = "0") Integer offset){
        return blogService.queryBlogOfFollow(max,offset);
    }


}
