package com.example.flashsale.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Token 服务。
 *
 * 当前只负责生成 Token，Token 校验会在下一步由拦截器完成。
 */
@Service
public class JwtTokenService {

    private final long expireSeconds;
    private final SecretKey signingKey;

    public JwtTokenService(
            @Value("${spring.jwt.secret}") String secret,
            @Value("${spring.jwt.expire-seconds}") long expireSeconds) {
        this.expireSeconds = expireSeconds;
        // HS256 要求密钥至少 256 bit（32 字节），配置中的密钥长度满足要求。
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 为用户生成 JWT。
     *
     * subject 保存用户 ID，过期时间防止 Token 永久有效。
     */
    public String generateToken(Long userId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireSeconds * 1000);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    /**
     * 解析 Token 中的用户 ID，下一阶段会使用这个方法保护接口。
     */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }
}
