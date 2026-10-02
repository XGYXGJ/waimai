package com.waimai.controller;

import com.waimai.common.context.UserContext;
import com.waimai.common.result.R;
import com.waimai.dto.WebDTO;
import com.waimai.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public R<Map<String, Object>> register(@RequestBody WebDTO.RegisterReq req) {
        return R.ok(authService.register(req));
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody WebDTO.LoginReq req) {
        return R.ok(authService.login(req.getPhone(), req.getPassword()));
    }

    @PostMapping("/refresh")
    public R<Map<String, Object>> refresh(@RequestBody WebDTO.RefreshReq req) {
        return R.ok(authService.refresh(req.getRefreshToken()));
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        return R.ok(authService.me(UserContext.userId()));
    }
}
