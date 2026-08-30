package com.henfon.shop.identity.security;

import com.henfon.shop.common.exception.BusinessException;
import org.springframework.security.core.Authentication;

/**
 * 门户会员认证主体解析器。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public final class MemberPrincipalResolver {

    private MemberPrincipalResolver() {
        // 工具类不允许实例化。
    }

    /**
     * 从当前认证上下文获取会员ID。
     *
     * @param authentication Spring Security 认证信息
     * @return 当前会员ID
     * @author Henfon
     * @date 2026-08-30
     */
    public static Long requireMemberId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)
                || !"MEMBER".equals(user.userType())) {
            throw new BusinessException("MEMBER_AUTH_REQUIRED", "请先登录会员账号");
        }
        return user.userId();
    }

    /**
     * 校验请求中的会员ID与认证主体一致。
     *
     * @param authentication Spring Security 认证信息
     * @param requestedMemberId 请求会员ID，可为空
     * @return 当前会员ID
     * @author Henfon
     * @date 2026-08-30
     */
    public static Long requireMemberId(Authentication authentication, Long requestedMemberId) {
        Long memberId = requireMemberId(authentication);
        if (requestedMemberId != null && !memberId.equals(requestedMemberId)) {
            throw new BusinessException("MEMBER_ID_MISMATCH", "请求会员与登录身份不一致");
        }
        return memberId;
    }
}
