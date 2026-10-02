package com.waimai.interceptor;

import com.waimai.common.annotation.PublicApi;
import com.waimai.common.annotation.RequireRole;
import com.waimai.common.context.UserContext;
import com.waimai.common.exception.BizException;
import com.waimai.common.result.ResultCode;
import com.waimai.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) return true;

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) token = token.substring(7);

        // 公开接口（@PublicApi）：匿名可访问，但若带了 token 仍解析登录态
        PublicApi publicApi = method.getMethodAnnotation(PublicApi.class);
        if (publicApi == null) publicApi = method.getBeanType().getAnnotation(PublicApi.class);
        if (publicApi != null && (token == null || token.isBlank())) {
            return true;
        }

        if (token == null || token.isBlank()) {
            throw new BizException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        String[] parsed = jwtUtil.parse(token);
        if (parsed == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }
        UserContext.set(Long.valueOf(parsed[0]), parsed[1]);

        // 角色校验：方法级注解优先，其次类级
        RequireRole anno = method.getMethodAnnotation(RequireRole.class);
        if (anno == null) anno = method.getBeanType().getAnnotation(RequireRole.class);
        if (anno != null) {
            String role = UserContext.role();
            if (role == null || !Arrays.asList(anno.value()).contains(role)) {
                throw new BizException(ResultCode.FORBIDDEN, "无权限访问");
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
