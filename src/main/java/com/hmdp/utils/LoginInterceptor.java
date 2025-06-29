package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1、判断是否需要拦截（ThreadLocal 中是否有用户）
        if (UserHolder.getUser() == null){
            // 没有用户，需要拦截，设置状态码
            response.setStatus(401);
            return false;
        }

        // 2、有用户，则放行
        return true;
    }
}
