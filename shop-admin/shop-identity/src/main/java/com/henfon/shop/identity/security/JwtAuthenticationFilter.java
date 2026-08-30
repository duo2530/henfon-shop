package com.henfon.shop.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bearer JWT 鉴权过滤器。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final MemberTokenStore memberTokenStore;

    /**
     * 创建 JWT 鉴权过滤器。
     *
     * @param jwtTokenService JWT 服务
     * @param memberTokenStore 会员令牌状态存储
     * @author Henfon
     * @date 2026-08-29
     */
    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, MemberTokenStore memberTokenStore) {
        this.jwtTokenService = jwtTokenService;
        this.memberTokenStore = memberTokenStore;
    }

    /**
     * 从请求头解析令牌并写入 Spring Security 上下文。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException IO 异常
     * @author Henfon
     * @date 2026-08-29
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                AuthenticatedUser user = jwtTokenService.parse(token);
                // 黑名单命中时保持匿名身份，由后续安全规则返回未授权。
                if (memberTokenStore.isAccessTokenRevoked(user.tokenId())) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
                var authorities = user.permissions().stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                var authentication = new UsernamePasswordAuthenticationToken(user, token, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ignored) {
                // 令牌无效时保持匿名状态，由安全配置返回 401，不向客户端暴露解析细节。
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
