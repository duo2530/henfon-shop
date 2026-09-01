package com.henfon.shop.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.config.MemberPasswordResetProperties;
import com.henfon.shop.identity.dto.MemberPasswordResetConfirmRequest;
import com.henfon.shop.identity.dto.MemberPasswordResetRequest;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import com.henfon.shop.identity.security.MemberTokenStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 会员邮箱找回密码服务。
 *
 * <p>申请接口始终返回统一结果，不暴露邮箱是否注册；确认接口消费 Redis 一次性令牌并更新密码。</p>
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Service
public class MemberPasswordResetService {

    private static final String RESET_SUBJECT = "会员登录密码重置";
    private final MemberUserMapper memberUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final MemberTokenStore memberTokenStore;
    private final ObjectProvider<MemberEmailSender> emailSenderProvider;
    private final MemberPasswordResetProperties properties;

    /**
     * 创建会员邮箱找回密码服务。
     *
     * @param memberUserMapper 会员数据访问对象
     * @param passwordEncoder 密码编码器
     * @param memberTokenStore 会员令牌 Redis 存储
     * @param emailSenderProvider 邮件发送适配器
     * @param properties 找回密码配置
     * @author Henfon
     * @date 2026-09-01
     */
    public MemberPasswordResetService(MemberUserMapper memberUserMapper,
                                      PasswordEncoder passwordEncoder,
                                      MemberTokenStore memberTokenStore,
                                      ObjectProvider<MemberEmailSender> emailSenderProvider,
                                      MemberPasswordResetProperties properties) {
        this.memberUserMapper = memberUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.memberTokenStore = memberTokenStore;
        this.emailSenderProvider = emailSenderProvider;
        this.properties = properties;
    }

    /**
     * 申请发送密码重置邮件。
     *
     * <p>无论邮箱是否存在都正常返回，防止通过响应枚举会员账号；存在账号时额外使用 Redis 冷却锁限制频率。</p>
     *
     * @param request 找回密码申请
     * @author Henfon
     * @date 2026-09-01
     */
    @Transactional(readOnly = true)
    public void requestReset(MemberPasswordResetRequest request) {
        String email = normalize(request == null ? null : request.email());
        if (email == null) {
            return;
        }
        // 未装配 SMTP 发送器时直接降级为统一成功响应，不生成无法送达的令牌。
        MemberEmailSender emailSender = emailSenderProvider.getIfAvailable();
        if (emailSender == null) {
            return;
        }
        MemberUser member = memberUserMapper.selectOne(new LambdaQueryWrapper<MemberUser>()
                .eq(MemberUser::getTenantId, 0L)
                .eq(MemberUser::getEmail, email)
                .eq(MemberUser::getStatus, 1)
                .last("LIMIT 1"));
        if (member == null || !StringUtils.hasText(member.getEmail())) {
            return;
        }
        Duration cooldown = Duration.ofSeconds(Math.max(1L, properties.getCooldownSeconds()));
        if (!memberTokenStore.tryAcquirePasswordResetCooldown(email, cooldown)) {
            return;
        }
        Duration ttl = Duration.ofMinutes(Math.max(1L, properties.getTtlMinutes()));
        String token = memberTokenStore.createPasswordResetToken(member.getId(), ttl);
        String resetUrl = buildResetUrl(token);
        String content = "您好，您正在重置 Henfon 商城会员登录密码。\n\n"
                + "请在 " + properties.getTtlMinutes() + " 分钟内打开以下链接完成重置：\n"
                + resetUrl + "\n\n"
                + "该链接仅可使用一次。如非本人操作，请忽略此邮件。";
        // 邮件适配器负责 SMTP 失败降级，认证接口不因邮件服务短暂故障泄露账号状态。
        emailSender.send(email, RESET_SUBJECT, content);
    }

    /**
     * 消费一次性令牌并更新会员密码。
     *
     * @param request 重置确认请求
     * @author Henfon
     * @date 2026-09-01
     */
    @Transactional
    public void confirmReset(MemberPasswordResetConfirmRequest request) {
        String token = request == null ? null : request.token();
        Long memberId = memberTokenStore.consumePasswordResetToken(token);
        if (memberId == null) {
            throw new BusinessException("MEMBER_PASSWORD_RESET_INVALID", "重置链接无效或已过期");
        }
        MemberUser member = memberUserMapper.selectById(memberId);
        if (member == null || !Integer.valueOf(1).equals(member.getStatus())) {
            throw new BusinessException("MEMBER_PASSWORD_RESET_INVALID", "重置链接无效或已过期");
        }
        // 令牌已在 Redis 原子消费后才进入此处，确保同一链接无法重复修改密码。
        member.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // 会员表暂未单独记录密码更新时间，使用通用更新时间保证审计字段同步刷新。
        member.setUpdatedAt(LocalDateTime.now());
        memberUserMapper.updateById(member);
    }

    /**
     * 拼接前端密码重置地址并编码令牌参数。
     *
     * @param token 一次性令牌
     * @return 可点击重置地址
     * @author Henfon
     * @date 2026-09-01
     */
    private String buildResetUrl(String token) {
        String baseUrl = StringUtils.hasText(properties.getUrl())
                ? properties.getUrl().trim() : "http://localhost:3000/?resetToken=";
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        if (baseUrl.endsWith("resetToken=")) {
            return baseUrl + encodedToken;
        }
        String separator = baseUrl.contains("?") ? (baseUrl.endsWith("?") || baseUrl.endsWith("&") ? "" : "&") : "?";
        return baseUrl + separator + "resetToken=" + encodedToken;
    }

    /**
     * 规范化邮箱输入。
     *
     * @param value 原始邮箱
     * @return 去空格后的邮箱或 null
     * @author Henfon
     * @date 2026-09-01
     */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
