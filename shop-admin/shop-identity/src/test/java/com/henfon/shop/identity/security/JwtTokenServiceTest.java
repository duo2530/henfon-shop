package com.henfon.shop.identity.security;

import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.entity.SysUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JWT 签发与解析安全回归测试。
 *
 * @author Henfon
 * @date 2026-08-31
 */
class JwtTokenServiceTest {

    private static final String SECRET = "henfon-shop-test-jwt-secret-key-change-me";

    /**
     * 校验会员令牌包含 MEMBER 类型并可还原会员主体。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldGenerateAndParseMemberToken() {
        JwtTokenService service = new JwtTokenService(properties(3600, "henfon-shop"));
        // 构造最小会员主体，模拟门户登录签发令牌。
        MemberUser member = new MemberUser();
        member.setId(1001L);
        member.setTenantId(7L);
        member.setUsername("member-1001");

        AuthenticatedUser authenticated = service.parse(service.generate(member));

        assertEquals(1001L, authenticated.userId());
        assertEquals(7L, authenticated.tenantId());
        assertEquals("MEMBER", authenticated.userType());
        assertEquals(List.of(), authenticated.permissions());
    }

    /**
     * 校验管理员令牌保留权限集合，供方法级权限控制使用。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldPreserveAdminPermissions() {
        JwtTokenService service = new JwtTokenService(properties(3600, "henfon-shop"));
        // 管理员令牌需要携带权限编码供 @PreAuthorize 判断。
        SysUser user = new SysUser();
        user.setId(2002L);
        user.setTenantId(9L);
        user.setUsername("admin-2002");

        AuthenticatedUser authenticated = service.parse(service.generate(user,
                List.of("catalog:product:query", "trade:order:ship")));

        assertEquals("ADMIN", authenticated.userType());
        assertEquals(List.of("catalog:product:query", "trade:order:ship"), authenticated.permissions());
    }

    /**
     * 校验签发方不一致的令牌会被拒绝，避免跨环境令牌重放。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectTokenFromAnotherIssuer() {
        JwtTokenService issuerA = new JwtTokenService(properties(3600, "issuer-a"));
        JwtTokenService issuerB = new JwtTokenService(properties(3600, "issuer-b"));
        // 使用相同签名密钥但不同 issuer，验证跨环境令牌不能互认。
        MemberUser member = new MemberUser();
        member.setId(1001L);
        member.setUsername("member-1001");

        String token = issuerA.generate(member);
        assertThrows(RuntimeException.class, () -> issuerB.parse(token));
    }

    /**
     * 校验过期令牌无法解析，防止过期访问令牌继续访问业务接口。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectExpiredToken() {
        JwtTokenService service = new JwtTokenService(properties(-1, "henfon-shop"));
        // 负有效期立即生成过期令牌，避免等待真实时钟推进。
        MemberUser member = new MemberUser();
        member.setId(1001L);
        member.setUsername("member-1001");

        String token = service.generate(member);
        assertThrows(RuntimeException.class, () -> service.parse(token));
    }

    /**
     * 校验弱于 256 bit 的 JWT 密钥会在启动时失败。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectWeakSecret() {
        JwtProperties properties = properties(3600, "too-short");
        // 模拟误配短密钥，构造服务时应主动阻断启动。
        properties.setSecret("too-short");
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenService(properties));
    }

    /**
     * 创建测试 JWT 配置。
     *
     * @param expirationSeconds 有效期秒数
     * @param issuer 签发方
     * @return JWT 配置
     * @author Henfon
     * @date 2026-08-31
     */
    private JwtProperties properties(long expirationSeconds, String issuer) {
        JwtProperties properties = new JwtProperties();
        // 测试密钥长度满足 HMAC-SHA256 最低要求。
        properties.setSecret(SECRET);
        properties.setExpirationSeconds(expirationSeconds);
        properties.setIssuer(issuer);
        return properties;
    }
}
