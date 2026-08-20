package com.jdec.platform.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** JWT 工具类 — 负责令牌的签发、解析与验证。 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:86400000}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * 签发 JWT 令牌。
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @param phone 手机号
     * @param extra 额外声明（可选）
     * @return JWT 字符串
     */
    public String generateToken(
            Long userId, String username, String phone, Map<String, Object> extra) {
        return generateToken(userId, username, phone, extra, expirationMs);
    }

    /**
     * 签发 JWT 令牌（指定过期时间）。
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @param phone 手机号
     * @param extra 额外声明（可选）
     * @param customExpirationMs 自定义过期时间（毫秒）
     * @return JWT 字符串
     */
    public String generateToken(
            Long userId,
            String username,
            String phone,
            Map<String, Object> extra,
            long customExpirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + customExpirationMs);

        var builder =
                Jwts.builder()
                        .subject(String.valueOf(userId))
                        .claim("username", username)
                        .claim("phone", phone)
                        .issuedAt(now)
                        .expiration(expiry);

        if (extra != null) {
            extra.forEach(builder::claim);
        }

        return builder.signWith(secretKey).compact();
    }

    /**
     * 解析 JWT 令牌，返回 Claims。
     *
     * @return Claims 或 null（令牌无效时）
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            log.warn("JWT 已过期: {}", ex.getMessage());
            return null;
        } catch (JwtException ex) {
            log.warn("JWT 解析失败: {}", ex.getMessage());
            return null;
        }
    }

    /** 从令牌中获取用户 ID。 */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims != null ? Long.parseLong(claims.getSubject()) : null;
    }
}
