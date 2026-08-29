package com.henfon.shop.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.dto.AdminLoginRequest;
import com.henfon.shop.identity.dto.AdminLoginResponse;
import com.henfon.shop.identity.entity.SysUser;
import com.henfon.shop.identity.mapper.SysUserMapper;
import com.henfon.shop.identity.mapper.SysUserRoleMapper;
import com.henfon.shop.identity.security.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端登录应用服务。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Service
public class AdminAuthService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    /**
     * 创建管理端登录服务。
     *
     * @param sysUserMapper 用户数据访问对象
     * @param sysUserRoleMapper 用户角色数据访问对象
     * @param passwordEncoder 密码编码器
     * @param jwtTokenService JWT 服务
     * @author Henfon
     * @date 2026-08-29
     */
    public AdminAuthService(SysUserMapper sysUserMapper, SysUserRoleMapper sysUserRoleMapper,
                            PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * 校验管理员账号并签发 JWT。
     *
     * @param request 登录请求
     * @param loginIp 客户端IP
     * @return 登录响应
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request, String loginIp) {
        long tenantId = request.tenantId() == null ? 0L : request.tenantId();
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getUsername, request.username())
                .last("LIMIT 1"));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("AUTH_INVALID", "用户名或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException("AUTH_DISABLED", "账号已被停用");
        }
        List<String> permissions = sysUserRoleMapper.selectPermissionCodesByUserId(user.getId());
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(loginIp);
        sysUserMapper.updateById(user);
        String token = jwtTokenService.generate(user, permissions);
        return new AdminLoginResponse(token, jwtTokenService.getExpirationSeconds(), user.getId(),
                user.getUsername(), user.getRealName(), permissions);
    }
}
