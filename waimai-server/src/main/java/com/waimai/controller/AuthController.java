package com.waimai.controller;

import com.waimai.common.context.UserContext;
import com.waimai.common.exception.BizException;
import com.waimai.common.result.R;
import com.waimai.common.result.ResultCode;
import com.waimai.common.util.RateLimitUtil;
import com.waimai.dto.WebDTO;
import com.waimai.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 登录/注册限流：同一 IP 60 秒内最多 10 次（设计文档第 9 章） */
    private static final int AUTH_RATE_LIMIT = 10;
    private static final int AUTH_RATE_WINDOW_SECONDS = 60;

    private final AuthService authService;
    private final RateLimitUtil rateLimitUtil;

    @PostMapping("/register")
    public R<Map<String, Object>> register(@RequestBody WebDTO.RegisterReq req, HttpServletRequest request) {
        checkRate(request, "register");
        return R.ok(authService.register(req));
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody WebDTO.LoginReq req, HttpServletRequest request) {
        checkRate(request, "login");
        return R.ok(authService.login(req.getPhone(), req.getPassword(), Boolean.TRUE.equals(req.getRemember())));
    }

    @PostMapping("/refresh")
    public R<Map<String, Object>> refresh(@RequestBody WebDTO.RefreshReq req) {
        return R.ok(authService.refresh(req.getRefreshToken()));
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        return R.ok(authService.me(UserContext.userId()));
    }

    /** IP 维度限流：挡撞库与批量注册；Redis 不可用时由 RateLimitUtil 自动放行 */
    private void checkRate(HttpServletRequest request, String scene) {
        String ip = RateLimitUtil.clientIp(request);
        if (!rateLimitUtil.allow("rl:" + scene + ":" + ip, AUTH_RATE_LIMIT, AUTH_RATE_WINDOW_SECONDS)) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "操作过于频繁，请 1 分钟后再试");
        }
    }
}
