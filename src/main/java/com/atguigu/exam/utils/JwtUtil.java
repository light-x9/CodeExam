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
 * JWT 工具类 —— 无状态认证的核心
 * 
 * =========================== 面试高频：什么是 JWT？ ===========================
 * 
 * JWT（JSON Web Token）是一个开放标准(RFC 7519)，用于在各方之间安全传输JSON对象。
 * 
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                     JWT 的结构（三段式）                          │
 * │                                                                 │
 * │  eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.xJgW1q...               │
 * │  ├──── Header ────┤ ├── Payload ──┤ ├── Signature ──┤          │
 * │                                                                 │
 * │  Header（头部）：{ "alg": "HS256", "typ": "JWT" }               │
 * │  Payload（载荷）：{ "userId": 1, "username": "admin", ... }     │
 * │  Signature（签名）：HMACSHA256(base64(header) + "." + base64(payload), secret) │
 * └─────────────────────────────────────────────────────────────────┘
 * 
 * ==================== 面试高频：有状态 vs 无状态 ====================
 * 
 * 有状态（Session）：
 *   客户端 → 登录 → 服务器创建 Session → 返回 SessionId(Cookie)
 *   客户端 → 请求 → 带 Cookie → 服务器查 Session → 获取用户信息
 *   问题：服务器存 Session → 内存开销大、分布式需要共享 Session、不支持跨域
 * 
 * 无状态（JWT）：
 *   客户端 → 登录 → 服务器生成 JWT → 返回 Token
 *   客户端 → 请求 → 带 Authorization: Bearer <token>
 *   服务器 → 解密 Token → 直接拿到用户信息（无需查库！）
 *   优势：服务器不存状态、天然支持分布式、跨域友好
 * 
 * ==================== 面试高频：JWT 如何防止篡改？ ====================
 * 
 * 客户端改了 payload（比如把 userId 从 1 改成 2），会发生什么？
 * → 服务器用 secret 重新计算签名，发现签名不匹配 → 拒绝请求
 * 因为客户端不知道 secret，没法生成正确的签名！
 * 
 * ==================== 面试高频：JWT 的缺点？ ====================
 * 
 * 1. Token 签发后无法主动失效（除非配合黑名单）
 * 2. Payload 只做 Base64 编码，不是加密，敏感信息不要放！
 * 3. Token 比 SessionId 长，每次请求多占带宽
 * 
 * @author 智能学习平台 - Level 3 无状态认证
 */
@Slf4j
@Component
public class JwtUtil {

    /**
     * JWT 签名密钥
     * HMAC-SHA256 要求密钥至少 256 bits = 32 字节
     * 从配置文件 jwt.secret 注入
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * Token 过期时间（毫秒），从配置文件 jwt.expiration 注入
     * 86400000 ms = 24 小时
     */
    @Value("${jwt.expiration}")
    private long expiration;

    /**
     * HTTP 请求头名称，如 "Authorization"
     */
    @Value("${jwt.header}")
    private String header;

    /**
     * Token 前缀，如 "Bearer "
     */
    @Value("${jwt.prefix}")
    private String prefix;

    /**
     * 获取签名密钥
     * 把配置的 secret 字符串转成 HMAC-SHA 密钥对象
     * 
     * 新版本 jjwt (>=0.12.0) 强制要求使用 SecretKey 对象，
     * 老版本直接用 String 的做法已被废弃
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ========================== Token 生成 ==========================

    /**
     * 生成 JWT Token
     * 
     * 流程：
     * 1. 组装 Payload（用户信息 + 过期时间 + 签发时间）
     * 2. 用 HMAC-SHA256 + secret 签名
     * 3. 返回三段式 Token 字符串
     * 
     * @param userId   用户ID
     * @param username 用户名
     * @param role     用户角色
     * @return JWT Token 字符串
     */
    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                // --- Payload：用户身份信息（Claims） ---
                .claim("userId", userId)      // 自定义 Claim：用户ID
                .claim("username", username)  // 自定义 Claim：用户名
                .claim("role", role)          // 自定义 Claim：角色
                // --- Payload：标准注册声明 ---
                .subject(username)            // sub：主题（令牌所属者）
                .issuedAt(now)                // iat：签发时间
                .expiration(expiryDate)       // exp：过期时间
                // --- 签名 ---
                .signWith(getSigningKey())    // 用 HMAC-SHA256 + secret 签名
                .compact();                   // 压缩成三段式字符串

        // 生成后的 Token 结构：
        // Header:  {"alg":"HS256","typ":"JWT"}
        // Payload: {"userId":1,"username":"admin","role":"ADMIN","sub":"admin","iat":...,"exp":...}
        // 三段用 . 连接，最后一段是签名
    }

    // ========================== Token 解析与校验 ==========================

    /**
     * 完整校验：从 Token 中解析出所有 Claims
     * 
     * 校验内容：
     * 1. 签名是否正确（防篡改）
     * 2. Token 是否过期
     * 3. Token 格式是否正确
     * 
     * 如果任何一项校验失败，抛出对应的 JwtException 子类
     * 
     * @param token JWT Token 字符串
     * @return Claims 对象，包含所有 Payload 数据
     * @throws ExpiredJwtException       Token 已过期
     * @throws MalformedJwtException     Token 格式错误
     * @throws SignatureException        签名不匹配（被篡改）
     * @throws IllegalArgumentException  Token 为空
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())  // 设置验证密钥
                .build()
                .parseSignedClaims(token)     // 解析并验证
                .getPayload();                // 获取 Payload
    }

    /**
     * 从 Token 中获取用户名
     * 先解析再提取，解析失败返回 null
     */
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.get("username", String.class);
        } catch (JwtException e) {
            log.warn("从 Token 获取用户名失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Token 中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            // JJWT 会把数字存成 Integer，需要转 Long
            return claims.get("userId", Long.class);
        } catch (JwtException e) {
            log.warn("从 Token 获取用户ID失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Token 中获取用户角色
     */
    public String getRoleFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.get("role", String.class);
        } catch (JwtException e) {
            log.warn("从 Token 获取角色失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 验证 Token 是否有效
     * 
     * @param token JWT Token 字符串
     * @return true=有效，false=无效或已过期
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            log.warn("JWT Token 验证失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查 Token 是否即将过期
     * 
     * @param token             JWT Token 字符串
     * @param refreshThresholdMs 剩余时间阈值（毫秒），如 3600000 = 1小时
     * @return true=即将过期，建议刷新 Token
     */
    public boolean isTokenExpiringSoon(String token, long refreshThresholdMs) {
        try {
            Claims claims = parseToken(token);
            Date expiration = claims.getExpiration();
            long remainingTime = expiration.getTime() - System.currentTimeMillis();
            return remainingTime > 0 && remainingTime < refreshThresholdMs;
        } catch (JwtException e) {
            return true; // 解析失败视为需要刷新
        }
    }

    /**
     * 获取 Token 的剩余有效时间（毫秒）
     * 
     * ==================== 用途：Redis 黑名单过期时间 ====================
     * 
     * 用户退出登录时，我们需要把 Token 加入 Redis 黑名单。
     * Redis key 的过期时间应该 = Token 剩余的有效时间，这样：
     * - Token 过期后，Redis 黑名单记录也被自动删除
     * - 不会在 Redis 里留下永久的垃圾数据
     * - 内存利用最大化
     * 
     * @param token JWT Token 字符串
     * @return 剩余有效时间（毫秒），最小返回 0（Token 已过期或无意义）
     */
    public long getTokenRemainingTime(String token) {
        try {
            Claims claims = parseToken(token);
            Date expiration = claims.getExpiration();
            long remainingTime = expiration.getTime() - System.currentTimeMillis();
            // 已经过期了，返回 0
            return Math.max(remainingTime, 0);
        } catch (ExpiredJwtException e) {
            // 特殊处理：Token 已过期的异常，jjwt 仍然可以从中提取过期时间
            // 直接用异常的 getClaims() 获取 Claims
            Date expiration = e.getClaims().getExpiration();
            long remainingTime = expiration.getTime() - System.currentTimeMillis();
            return Math.max(remainingTime, 0);
        } catch (JwtException e) {
            // Token 格式错误、签名不对等情况，返回 0
            log.warn("无法获取 Token 剩余时间：{}", e.getMessage());
            return 0;
        }
    }

    // ========================== 请求头处理 ==========================

    /**
     * 从 HTTP 请求头中提取 Token
     * 
     * 请求头格式：Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
     *                                               ↑ 注意 Bearer 后面有个空格
     * 
     * @param authHeader Authorization 请求头的值
     * @return 纯 Token 字符串（去掉 "Bearer " 前缀），如果格式不对返回 null
     */
    public String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith(prefix)) {
            return authHeader.substring(prefix.length());
        }
        return null;
    }

    // ========================== Getter（供外部使用） ==========================

    public String getHeader() {
        return header;
    }

    public String getPrefix() {
        return prefix;
    }
}
