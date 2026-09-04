package com.henfon.shop.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.config.MemberSmsProperties;
import com.henfon.shop.identity.dto.MemberSmsCodeResponse;
import com.henfon.shop.identity.dto.MemberSmsLoginRequest;
import com.henfon.shop.identity.dto.MemberLoginResponse;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import com.henfon.shop.identity.security.MemberTokenStore;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 会员短信验证码应用服务。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@Service
public class MemberSmsService {
    private final MemberUserMapper memberUserMapper;
    private final MemberTokenStore tokenStore;
    private final MemberAuthService memberAuthService;
    private final MemberSmsProperties properties;
    private final SecureRandom random = new SecureRandom();

    public MemberSmsService(MemberUserMapper memberUserMapper, MemberTokenStore tokenStore,
                            MemberAuthService memberAuthService, MemberSmsProperties properties) {
        this.memberUserMapper = memberUserMapper;
        this.tokenStore = tokenStore;
        this.memberAuthService = memberAuthService;
        this.properties = properties;
    }

    /**
     * 发送会员短信验证码。
     *
     * @param phone 手机号
     * @return 验证码响应
     * @author Henfon
     * @date 2026-09-04
     */
    public MemberSmsCodeResponse sendCode(String phone) {
        String normalized = normalizePhone(phone);
        if (!tokenStore.tryAcquireSmsCooldown(normalized, Duration.ofSeconds(properties.getCooldownSeconds()))) {
            throw new BusinessException("MEMBER_SMS_RATE_LIMITED", "验证码发送过于频繁，请稍后再试");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        tokenStore.saveSmsCode(normalized, code, Duration.ofSeconds(properties.getTtlSeconds()));
        // 当前未接入短信供应商，开发环境返回验证码以便联调；生产环境应关闭 expose-code。
        return new MemberSmsCodeResponse(normalized, properties.getTtlSeconds(), properties.isExposeCode() ? code : null);
    }

    /**
     * 校验短信验证码并签发会员令牌。
     *
     * @param request 短信登录请求
     * @return 登录令牌和会员摘要
     * @author Henfon
     * @date 2026-09-04
     */
    public MemberLoginResponse login(MemberSmsLoginRequest request) {
        String phone = normalizePhone(request.phone());
        int verifyResult = tokenStore.verifySmsCode(phone, request.verificationCode(), properties.getMaxVerifyAttempts(),
                Duration.ofSeconds(properties.getTtlSeconds()));
        if (verifyResult != 1) {
            // 统一返回错误信息，避免泄露验证码是否存在；错误次数达到阈值后验证码已被 Redis 原子失效。
            throw new BusinessException("MEMBER_SMS_INVALID", "验证码错误或已过期");
        }
        MemberUser member = memberUserMapper.selectOne(new LambdaQueryWrapper<MemberUser>()
                .eq(MemberUser::getTenantId, 0L).eq(MemberUser::getPhone, phone).last("LIMIT 1"));
        if (member == null) throw new BusinessException("MEMBER_NOT_FOUND", "手机号尚未注册");
        if (!Integer.valueOf(1).equals(member.getStatus())) throw new BusinessException("MEMBER_AUTH_DISABLED", "会员账号已被冻结");
        return memberAuthService.loginByMember(member);
    }

    /**
     * 规范化手机号，兼容中国区号前缀与常见分隔符。
     *
     * @param phone 原始手机号
     * @return 规范化手机号
     * @author Henfon
     * @date 2026-09-04
     */
    private String normalizePhone(String phone) {
        String normalized = phone == null ? "" : phone.trim().replace(" ", "").replace("-", "");
        if (normalized.startsWith("+86")) normalized = normalized.substring(3);
        return normalized;
    }
}
