package com.hmdp.config;

import com.hmdp.dto.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 根据 Result.success 的值自动修改 HTTP 状态码
 */
@RestControllerAdvice
public class ResultResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        // 只对返回类型是 Result 的接口生效
        return returnType.getParameterType().equals(Result.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class selectedConverterType, ServerHttpRequest request,
                                  ServerHttpResponse response) {

        if (body instanceof Result) {
            Result result = (Result) body;
            if (!result.getSuccess()) {
                // 如果是失败结果，则设置 HTTP 状态码为 400
                response.setStatusCode(HttpStatus.BAD_REQUEST);
            }
        }

        return body;
    }
}
