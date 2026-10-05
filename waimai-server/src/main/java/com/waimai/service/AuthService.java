package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.waimai.common.exception.BizException;
import com.waimai.common.result.ResultCode;
import com.waimai.common.util.JwtUtil;
import com.waimai.dto.WebDTO;
import com.waimai.entity.Merchant;
import com.waimai.entity.Rider;
import com.waimai.entity.User;
import com.waimai.mapper.MerchantMapper;
import com.waimai.mapper.RiderMapper;
import com.waimai.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Set<String> REGISTERABLE = Set.of("USER", "MERCHANT", "RIDER");

    /** 勾选「30 天免登录」后 refresh token 的有效天数（硬过期：刷新不延长总时长） */
    private static final long REMEMBER_REFRESH_DAYS = 30;
    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;
    private final RiderMapper riderMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Transactional
    public Map<String, Object> register(WebDTO.RegisterReq req) {
        if (req.getPhone() == null || req.getPhone().isBlank()
                || req.getPassword() == null || req.getPassword().length() < 6) {
            throw new BizException(ResultCode.PARAM_ERROR, "手机号必填，密码至少6位");
        }
        String role = req.getRole() == null ? "USER" : req.getRole();
        if (!REGISTERABLE.contains(role)) {
            throw new BizException(ResultCode.PARAM_ERROR, "不支持的注册角色");
        }
        Long cnt = userMapper.selectCount(new QueryWrapper<User>().eq("phone", req.getPhone()));
        if (cnt > 0) throw new BizException("该手机号已注册");

        User user = new User();
        user.setPhone(req.getPhone());
        user.setPasswordHash(encoder.encode(req.getPassword()));
        user.setNickname(req.getNickname() == null || req.getNickname().isBlank() ? "用户" + req.getPhone().substring(7) : req.getNickname());
        user.setRole(role);
        userMapper.insert(user);

        // 商户/骑手注册需管理员审核
        if ("MERCHANT".equals(role)) {
            Merchant m = new Merchant();
            m.setUserId(user.getId());
            m.setShopName(user.getNickname() + "的店铺");
            m.setCategoryId(1);
            m.setPhone(req.getPhone());
            m.setAddress("待完善");
            m.setLng(java.math.BigDecimal.ZERO);
            m.setLat(java.math.BigDecimal.ZERO);
            m.setAuditStatus(0);
            merchantMapper.insert(m);
        } else if ("RIDER".equals(role)) {
            Rider r = new Rider();
            r.setUserId(user.getId());
            r.setRealName(user.getNickname());
            r.setPhone(req.getPhone());
            r.setAuditStatus(0);
            riderMapper.insert(r);
        }
        return login(req.getPhone(), req.getPassword());
    }

    public Map<String, Object> login(String phone, String password) {
        return login(phone, password, false);
    }

    /**
     * 登录。
     * @param remember true = 「30 天免登录」，签发长期 refresh token；false = 默认短期
     */
    public Map<String, Object> login(String phone, String password, boolean remember) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("phone", phone));
        if (user == null || !encoder.matches(password, user.getPasswordHash())) {
            throw new BizException("手机号或密码错误");
        }
        if (user.getStatus() == 0) throw new BizException("账号已被封禁，请联系平台");

        String refreshToken = remember
                ? jwtUtil.genRefreshToken(user.getId(), user.getRole(), REMEMBER_REFRESH_DAYS)
                : jwtUtil.genRefreshToken(user.getId(), user.getRole());
        java.util.Date refreshExp = jwtUtil.expiration(refreshToken);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("accessToken", jwtUtil.genAccessToken(user.getId(), user.getRole()));
        resp.put("refreshToken", refreshToken);
        // 前端据此知道「免登录」到什么时候，不必自己解 JWT
        resp.put("refreshExpiresAt", refreshExp == null ? null : refreshExp.getTime());
        resp.put("remember", remember);
        resp.put("profile", buildProfile(user));
        return resp;
    }

    public Map<String, Object> refresh(String refreshToken) {
        String[] parsed = jwtUtil.parse(refreshToken);
        if (parsed == null) throw new BizException(ResultCode.UNAUTHORIZED, "refreshToken无效");
        // 硬过期：沿用旧凭证的原始到期时间，刷新只换新的 access token，不把总登录时长往后推
        java.util.Date expiresAt = jwtUtil.expiration(refreshToken);
        if (expiresAt == null || expiresAt.before(new java.util.Date())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }
        User user = userMapper.selectById(Long.valueOf(parsed[0]));
        if (user == null || user.getStatus() == 0) throw new BizException(ResultCode.UNAUTHORIZED, "账号不可用");
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("accessToken", jwtUtil.genAccessToken(user.getId(), user.getRole()));
        resp.put("refreshToken", jwtUtil.genRefreshTokenUntil(user.getId(), user.getRole(), expiresAt));
        resp.put("refreshExpiresAt", expiresAt.getTime());
        return resp;
    }

    public Map<String, Object> me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED, "用户不存在");
        return buildProfile(user);
    }

    private Map<String, Object> buildProfile(User user) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("userId", user.getId());
        p.put("phone", user.getPhone());
        p.put("nickname", user.getNickname());
        p.put("avatar", user.getAvatar());
        p.put("role", user.getRole());
        if ("MERCHANT".equals(user.getRole())) {
            Merchant m = merchantMapper.selectOne(new QueryWrapper<Merchant>().eq("user_id", user.getId()));
            p.put("merchantId", m == null ? null : m.getId());
            p.put("auditStatus", m == null ? null : m.getAuditStatus());
        } else if ("RIDER".equals(user.getRole())) {
            Rider r = riderMapper.selectOne(new QueryWrapper<Rider>().eq("user_id", user.getId()));
            p.put("riderId", r == null ? null : r.getId());
            p.put("auditStatus", r == null ? null : r.getAuditStatus());
        }
        return p;
    }
}
