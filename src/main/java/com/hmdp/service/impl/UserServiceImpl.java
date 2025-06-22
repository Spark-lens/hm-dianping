package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1、校验手机号
        if (!RegexUtils.isPhoneInvalid(phone)){
            // 2、手机号不符合，返回错误信息
            return Result.fail("手机号格式校验不符合！");
        }
        // 3、手机号符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // TODO 4、保存验证码到 redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 5、发送验证码
        log.info("发送短信验证码成功！验证码：" + code);
        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1、校验手机号
        String phone = loginForm.getPhone();
        if (!RegexUtils.isPhoneInvalid(phone)){
            // 1、手机号不符合，返回错误信息
            return Result.fail("手机号格式校验不符合！");
        }
        // 2、校验验证码
        Object code = session.getAttribute("code");
        String cacheCode = loginForm.getCode();
        if (cacheCode == null && cacheCode.equals(code) && RegexUtils.isCodeInvalid(cacheCode)){
            // 2、验证码不一致，报错
            return Result.fail("验证码不符合！");
        }

        // 3、一致，根据手机号查询用户
        // select * from user where phone = ?
        User user = query().eq("phone", phone).one();

        // 4、判断用户是否存在
        if (user == null){
            // 4、不存在，创建新用户并插入到数据库
            log.debug("用户不存在，创建新用户！");
            user = createUserWithPhone(phone);
        }


        // 5、保存用户到 session
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));


        // TODO 5、保存用户信息到 redis中
        // TODO 5.1、随机生成 token ，作为登录令牌
        String token = UUID.randomUUID().toString();

        // TODO 5.2、将 User 对象转为 HashMap 存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, filedValue) -> filedValue.toString()));

        // TODO 5.3、Hash 存储 用户信息
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);

        // TODO 5.4、设置 token 有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);

        // TODO 6、返回 token
        return Result.ok(token);
    }

    /**
     * 创建新用户并插入到数据库
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        // 创建新用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 保存到数据库
        save(user);
        return user;
    }

    /**
     * 根据用户id查询用户信息
     * @param userId 用户id
     * @return
     */
    @Override
    public Result queryUserById(Long userId) {
        // 1、获取用户信息
        User user = getById(userId);
        // 2、判断用户是否存在
        if (user == null){
            return Result.fail("用户不存在！");
        }
        // 3、用户信息封装到 UserDTO，返回
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }


    /**
     * 用户签到
     * @return
     */
    @Override
    public Result sign() {
        // 1、获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2、获取当前日期
        LocalDateTime now = LocalDateTime.now();
        // 3、拼接 key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4、获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5、写入 redis
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth - 1,true);
        return Result.ok();
    }


    /**
     * 每月签到统计
     * @return
     */
    @Override
    public Result signCount() {
        // 1、获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2、获取当前日期
        LocalDateTime now = LocalDateTime.now();
        // 3、拼接 key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4、获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();

        // 5、获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202506 GET u30 0
        List<Long> result = stringRedisTemplate.opsForValue()
                .bitField(
                        key,
                        BitFieldSubCommands
                                .create()
                                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                                .valueAt(0));
        if (result == null || result.isEmpty()){
            // 没有任何签到结果
            return Result.ok(0);
        }

        Long num = result.get(0);
        if (num == null || num == 0){
            return Result.ok(0);
        }

        // 6、循环遍历
        int count = 0;
        while (true){
            // 6.1、让这个数字与 1 做与运算，得到数字的最后一个 bit 位，判断这个 bit 位是否为0
            if ((num & 1) == 0){
                // 如果为0，说明未签到，结束
                break;
            }else {
                // 如果不为0，说明已签到，计数器 +1
                count ++;
            }
            // 把数字右移一位，抛弃最后一个 bit 位，继续下一个 bit 位
            num >>>= 1;
        }
        return Result.ok(count);
    }
}
