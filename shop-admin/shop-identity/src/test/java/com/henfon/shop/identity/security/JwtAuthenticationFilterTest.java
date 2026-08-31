package com.henfon.shop.identity.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JWT 过滤器安全回归测试。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private MemberTokenStore memberTokenStore;

    @Mock
    private FilterChain filterChain;

    /**
     * 每个测试结束后清理线程上下文，避免认证状态串扰。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @AfterEach
    void clearSecurityContext() {
        // SecurityContextHolder 基于线程存储，必须在每个测试后清理。
        SecurityContextHolder.clearContext();
    }

    /**
     * 校验有效 Bearer 令牌会写入认证主体和权限。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldAuthenticateValidBearerToken() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(10L, 1L, "admin",
                List.of("catalog:product:query"), "ADMIN", "jti-10");
        // 模拟签名校验成功且令牌未被 Redis 黑名单撤销。
        when(jwtTokenService.parse("valid-token")).thenReturn(user);
        when(memberTokenStore.isAccessTokenRevoked("jti-10")).thenReturn(false);

        invokeFilter("valid-token");

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(user, authentication.getPrincipal());
        assertEquals("catalog:product:query", authentication.getAuthorities().iterator().next().getAuthority());
        verify(filterChain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /**
     * 校验访问令牌黑名单命中时不会恢复认证，防止退出后的令牌重放。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectRevokedAccessToken() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(10L, 1L, "admin", List.of(), "ADMIN", "jti-10");
        // 模拟退出后 jti 命中黑名单，过滤器必须保持匿名。
        when(jwtTokenService.parse("revoked-token")).thenReturn(user);
        when(memberTokenStore.isAccessTokenRevoked("jti-10")).thenReturn(true);

        invokeFilter("revoked-token");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /**
     * 校验签名错误或格式非法的令牌会被静默清理，不向客户端暴露解析细节。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldIgnoreInvalidToken() throws Exception {
        // 模拟签名错误，过滤器应吞掉解析异常并交给后续安全规则返回 401。
        when(jwtTokenService.parse("invalid-token")).thenThrow(new IllegalArgumentException("bad signature"));

        invokeFilter("invalid-token");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /**
     * 使用指定令牌执行过滤器。
     *
     * @param token Bearer 令牌
     * @author Henfon
     * @date 2026-08-31
     */
    private void invokeFilter(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // 使用真实 Servlet mock，覆盖 Authorization 头解析路径。
        request.addHeader("Authorization", "Bearer " + token);
        new JwtAuthenticationFilter(jwtTokenService, memberTokenStore)
                .doFilterInternal(request, new MockHttpServletResponse(), filterChain);
    }
}
