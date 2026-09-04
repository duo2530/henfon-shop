package com.henfon.shop.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.dto.MemberLoginRequest;
import com.henfon.shop.identity.dto.MemberLoginResponse;
import com.henfon.shop.identity.dto.MemberRegisterRequest;
import com.henfon.shop.identity.dto.MemberRefreshRequest;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import com.henfon.shop.identity.security.JwtTokenService;
import com.henfon.shop.identity.security.AuthenticatedUser;
import com.henfon.shop.identity.security.MemberTokenStore;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 门户会员认证应用服务。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Service
public class MemberAuthService {

    private final MemberUserMapper memberUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final MemberTokenStore memberTokenStore;

    /**
     * 创建会员认证服务。
     *
     * @param memberUserMapper 会员数据访问对象
     * @param passwordEncoder 密码编码器
     * @param jwtTokenService JWT 服务
     * @author Henfon
     * @date 2026-08-30
     */
    public MemberAuthService(MemberUserMapper memberUserMapper, PasswordEncoder passwordEncoder,
                             JwtTokenService jwtTokenService, MemberTokenStore memberTokenStore) {
        this.memberUserMapper = memberUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.memberTokenStore = memberTokenStore;
    }

    /**
     * 校验会员账号并签发 JWT。
     *
     * @param request 登录请求
     * @return 登录响应
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public MemberLoginResponse login(MemberLoginRequest request) {
        String account = request.account().trim();
        // 登录账号统一匹配用户名、手机号和邮箱，避免前端区分登录入口。
        MemberUser member = memberUserMapper.selectOne(new LambdaQueryWrapper<MemberUser>()
                .eq(MemberUser::getTenantId, 0L)
                .and(query -> query.eq(MemberUser::getUsername, account)
                        .or().eq(MemberUser::getPhone, account)
                        .or().eq(MemberUser::getEmail, account))
                .last("LIMIT 1"));
        if (member == null || !StringUtils.hasText(member.getPasswordHash())
                || !passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw new BusinessException("MEMBER_AUTH_INVALID", "账号或密码错误");
        }
        if (!Integer.valueOf(1).equals(member.getStatus())) {
            throw new BusinessException("MEMBER_AUTH_DISABLED", "会员账号已被冻结");
        }
        return loginByMember(member);
    }

    /**
     * 更新会员登录时间并签发令牌，供密码和短信登录复用。
     *
     * @param member 已校验的会员
     * @return 登录响应
     * @author Henfon
     * @date 2026-09-04
     */
    @Transactional
    public MemberLoginResponse loginByMember(MemberUser member) {
        // 统一记录最近登录时间，保证不同认证方式的会员资料一致。
        member.setLastLoginAt(LocalDateTime.now());
        memberUserMapper.updateById(member);
        return issueToken(member);
    }

    /**
     * 注册门户会员并自动登录。
     *
     * @param request 注册请求
     * @return 登录响应
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public MemberLoginResponse register(MemberRegisterRequest request) {
        // 先规范化可选字段，再执行重复校验，确保空格不会绕过唯一性检查。
        String username = request.username().trim();
        String nickname = request.nickname().trim();
        String phone = normalize(request.phone());
        String email = normalize(request.email());
        long duplicateCount = memberUserMapper.selectCount(new LambdaQueryWrapper<MemberUser>()
                .eq(MemberUser::getTenantId, 0L)
                .and(query -> query.eq(MemberUser::getUsername, username)
                        .or(phone != null, q -> q.eq(MemberUser::getPhone, phone))
                        .or(email != null, q -> q.eq(MemberUser::getEmail, email))));
        if (duplicateCount > 0) {
            throw new BusinessException("MEMBER_EXISTS", "用户名、手机号或邮箱已注册");
        }
        // 新会员使用统一的默认等级、积分和余额初始化。
        MemberUser member = new MemberUser();
        member.setTenantId(0L);
        member.setMemberNo("M" + IdWorker.getIdStr());
        member.setUsername(username);
        member.setPasswordHash(passwordEncoder.encode(request.password()));
        member.setNickname(nickname);
        member.setPhone(phone);
        member.setEmail(email);
        member.setMemberLevel("REGULAR");
        member.setPoints(0L);
        member.setBalance(java.math.BigDecimal.ZERO);
        member.setStatus(1);
        member.setRegisteredAt(LocalDateTime.now());
        memberUserMapper.insert(member);
        return issueToken(member);
    }

    /**
     * 使用刷新令牌轮换会员访问令牌。
     *
     * @param request 刷新请求
     * @return 新的登录令牌
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public MemberLoginResponse refresh(MemberRefreshRequest request) {
        Long memberId = memberTokenStore.getMemberId(request.refreshToken());
        if (memberId == null) {
            throw new BusinessException("MEMBER_REFRESH_INVALID", "刷新令牌无效或已过期");
        }
        MemberUser member = memberUserMapper.selectById(memberId);
        if (member == null || !Integer.valueOf(1).equals(member.getStatus())) {
            throw new BusinessException("MEMBER_AUTH_DISABLED", "会员账号已被冻结");
        }
        // 刷新令牌采用一次性轮换，旧令牌删除后再签发新的一对令牌。
        memberTokenStore.deleteRefreshToken(request.refreshToken());
        return issueToken(member);
    }

    /**
     * 注销当前会员访问令牌和刷新令牌。
     *
     * @param authentication 当前认证信息
     * @param refreshToken 刷新令牌
     * @author Henfon
     * @date 2026-08-30
     */
    public void logout(Authentication authentication, String refreshToken) {
        // 访问令牌和刷新令牌分别失效，兼容访问令牌已过期的退出请求。
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            memberTokenStore.revokeAccessToken(user.tokenId());
        }
        memberTokenStore.deleteRefreshToken(refreshToken);
    }

    /**
     * 组装会员登录响应。
     *
     * @param member 会员实体
     * @param token JWT
     * @return 登录响应
     * @author Henfon
     * @date 2026-08-30
     */
    private MemberLoginResponse toResponse(MemberUser member, String token) {
        return new MemberLoginResponse(token, jwtTokenService.getExpirationSeconds(),
                memberTokenStore.createRefreshToken(member.getId()), member.getId(),
                member.getUsername(), member.getNickname(), member.getMemberLevel(), member.getPoints(),
                member.getBalance(), member.getPhone(), member.getEmail(), member.getAvatarUrl());
    }

    /**
     * 签发访问令牌并创建刷新令牌。
     *
     * @param member 会员实体
     * @return 登录响应
     * @author Henfon
     * @date 2026-08-30
     */
    private MemberLoginResponse issueToken(MemberUser member) {
        return toResponse(member, jwtTokenService.generate(member));
    }

    /**
     * 规范化可选文本字段。
     *
     * @param value 原始值
     * @return 去空格后的值或 null
     * @author Henfon
     * @date 2026-08-30
     */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
