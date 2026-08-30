package com.henfon.shop.identity.security;

import com.henfon.shop.identity.entity.SysUser;
import com.henfon.shop.identity.entity.MemberUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT 令牌生成与解析服务。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Service
public class JwtTokenService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    /**
     * 创建 JWT 服务并初始化签名密钥。
     *
     * @param properties JWT 配置
     * @author Henfon
     * @date 2026-08-29
     */
    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        // HMAC-SHA 密钥至少需要 256 bit，配置不足时直接阻止应用以避免弱密钥运行。
        byte[] secret = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalArgumentException("shop.security.jwt.secret 长度至少为32字节");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret);
    }

    /**
     * 为登录用户签发访问令牌。
     *
     * @param user 用户实体
     * @param permissions 权限编码
     * @return JWT 字符串
     * @author Henfon
     * @date 2026-08-29
     */
    public String generate(SysUser user, Collection<String> permissions) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(properties.getExpirationSeconds());
        // 为每次签发生成独立 jti，支持管理员和会员访问令牌主动失效。
        String tokenId = UUID.randomUUID().toString();
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("tenantId", user.getTenantId())
                .claim("userType", "ADMIN")
                .id(tokenId)
                .claim("permissions", permissions == null ? List.of() : List.copyOf(permissions))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    /**
     * 为门户会员签发访问令牌。
     *
     * @param member 会员实体
     * @return JWT 字符串
     * @author Henfon
     * @date 2026-08-30
     */
    public String generate(MemberUser member) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(properties.getExpirationSeconds());
        // 会员令牌携带用户类型，供门户接口拒绝管理员令牌越权访问。
        String tokenId = UUID.randomUUID().toString();
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(member.getId()))
                .claim("username", member.getUsername())
                .claim("tenantId", member.getTenantId())
                .claim("userType", "MEMBER")
                .claim("permissions", List.of())
                .id(tokenId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    /**
     * 解析并校验访问令牌。
     *
     * @param token JWT 字符串
     * @return 认证主体
     * @author Henfon
     * @date 2026-08-29
     */
    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Object permissionClaim = claims.get("permissions");
        List<String> permissions = permissionClaim instanceof List<?> values
                ? values.stream().map(String::valueOf).toList()
                : List.of();
        Number tenantId = claims.get("tenantId", Number.class);
        Object userTypeClaim = claims.get("userType");
        return new AuthenticatedUser(Long.valueOf(claims.getSubject()),
                tenantId == null ? 0L : tenantId.longValue(),
                claims.get("username", String.class), permissions,
                userTypeClaim == null ? "ADMIN" : String.valueOf(userTypeClaim), claims.getId());
    }

    /**
     * 获取令牌有效期。
     *
     * @return 有效期秒数
     * @author Henfon
     * @date 2026-08-29
     */
    public long getExpirationSeconds() {
        return properties.getExpirationSeconds();
    }
}
