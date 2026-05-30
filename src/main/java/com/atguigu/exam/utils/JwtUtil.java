package com.atguigu.exam.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类 - 无状态认证的核心（jjwt 0.12.x API）
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.header}")
    private String header;

    @Value("${jwt.prefix}")
    private String prefix;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 JWT Token
     */
    public String generateToken(Long userId, String username, String studentNo, String role) {
        return generateToken(userId, username, studentNo, null, role);
    }

    /**
     * 生成 JWT Token（包含真实姓名）
     */
    public String generateToken(Long userId, String username, String studentNo, String realName, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claim("userId", userId)
                .claim("username", username)
                .claim("studentNo", studentNo)
                .claim("realName", realName)
                .claim("role", role)
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 Token（jjwt 0.12.x API）
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserIdFromToken(String token) {
        try {
            return parseToken(token).get("userId", Long.class);
        } catch (JwtException e) {
            log.warn("从 Token 获取用户ID失败：{}", e.getMessage());
            return null;
        }
    }

    public String getUsernameFromToken(String token) {
        try {
            return parseToken(token).get("username", String.class);
        } catch (JwtException e) {
            return null;
        }
    }

    public String getStudentNoFromToken(String token) {
        try {
            return parseToken(token).get("studentNo", String.class);
        } catch (JwtException e) {
            return null;
        }
    }

    /**
     * 从 Token 中获取真实姓名
     */
    public String getRealNameFromToken(String token) {
        try {
            return parseToken(token).get("realName", String.class);
        } catch (JwtException e) {
            return null;
        }
    }

    public String getRoleFromToken(String token) {
        try {
            return parseToken(token).get("role", String.class);
        } catch (JwtException e) {
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            log.warn("JWT Token 验证失败：{}", e.getMessage());
            return false;
        }
    }

    public long getTokenRemainingTime(String token) {
        try {
            Date expiration = parseToken(token).getExpiration();
            return Math.max(expiration.getTime() - System.currentTimeMillis(), 0);
        } catch (ExpiredJwtException e) {
            return Math.max(e.getClaims().getExpiration().getTime() - System.currentTimeMillis(), 0);
        } catch (JwtException e) {
            return 0;
        }
    }

    public String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith(prefix)) {
            return authHeader.substring(prefix.length());
        }
        return null;
    }

    public String getHeader() { return header; }
    public String getPrefix() { return prefix; }
}
