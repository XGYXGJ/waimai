package com.waimai.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${waimai.jwt.secret}")
    private String secret;

    @Value("${waimai.jwt.access-ttl-hours}")
    private long accessTtlHours;

    @Value("${waimai.jwt.refresh-ttl-days}")
    private long refreshTtlDays;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String genAccessToken(Long userId, String role) {
        return gen(userId, role, accessTtlHours * 3600_000L);
    }

    public String genRefreshToken(Long userId, String role) {
        return gen(userId, role, refreshTtlDays * 24 * 3600_000L);
    }

    /** 按指定天数签发 refresh token（「N 天免登录」用） */
    public String genRefreshToken(Long userId, String role, long days) {
        return gen(userId, role, days * 24 * 3600_000L);
    }

    /**
     * 按指定绝对到期时间签发 refresh token。
     * 刷新时沿用旧凭证的到期时间 → 实现「硬过期」：刷新只换新的 access token，不延长总登录时长。
     */
    public String genRefreshTokenUntil(Long userId, String role, Date expiration) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key())
                .compact();
    }

    /** 取 token 的到期时间；无效/已过期返回 null */
    public Date expiration(String token) {
        try {
            return Jwts.parser().verifyWith(key()).build()
                    .parseSignedClaims(token).getPayload().getExpiration();
        } catch (Exception e) {
            return null;
        }
    }

    private String gen(Long userId, String role, long ttlMillis) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key())
                .compact();
    }

    /** 解析 token，返回 [userId, role]；无效返回 null */
    public String[] parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key()).build()
                    .parseSignedClaims(token).getPayload();
            return new String[]{c.getSubject(), c.get("role", String.class)};
        } catch (Exception e) {
            return null;
        }
    }
}
