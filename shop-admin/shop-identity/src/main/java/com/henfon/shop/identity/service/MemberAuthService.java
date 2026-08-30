package com.henfon.shop.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.dto.MemberLoginRequest;
import com.henfon.shop.identity.dto.MemberLoginResponse;
import com.henfon.shop.identity.dto.MemberRegisterRequest;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import com.henfon.shop.identity.security.JwtTokenService;
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
                             JwtTokenService jwtTokenService) {
        this.memberUserMapper = memberUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
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
        member.setLastLoginAt(LocalDateTime.now());
        memberUserMapper.updateById(member);
        return toResponse(member, jwtTokenService.generate(member));
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
        long duplicateCount = memberUserMapper.selectCount(new LambdaQueryWrapper<MemberUser>()
                .eq(MemberUser::getTenantId, 0L)
                .and(query -> query.eq(MemberUser::getUsername, request.username())
                        .or(StringUtils.hasText(request.phone()), q -> q.eq(MemberUser::getPhone, request.phone()))
                        .or(StringUtils.hasText(request.email()), q -> q.eq(MemberUser::getEmail, request.email()))));
        if (duplicateCount > 0) {
            throw new BusinessException("MEMBER_EXISTS", "用户名、手机号或邮箱已注册");
        }
        MemberUser member = new MemberUser();
        member.setTenantId(0L);
        member.setMemberNo("M" + IdWorker.getIdStr());
        member.setUsername(request.username().trim());
        member.setPasswordHash(passwordEncoder.encode(request.password()));
        member.setNickname(request.nickname().trim());
        member.setPhone(normalize(request.phone()));
        member.setEmail(normalize(request.email()));
        member.setMemberLevel("REGULAR");
        member.setPoints(0L);
        member.setBalance(java.math.BigDecimal.ZERO);
        member.setStatus(1);
        member.setRegisteredAt(LocalDateTime.now());
        memberUserMapper.insert(member);
        return toResponse(member, jwtTokenService.generate(member));
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
        return new MemberLoginResponse(token, jwtTokenService.getExpirationSeconds(), member.getId(),
                member.getUsername(), member.getNickname(), member.getMemberLevel(), member.getPoints(),
                member.getBalance(), member.getPhone(), member.getEmail(), member.getAvatarUrl());
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
