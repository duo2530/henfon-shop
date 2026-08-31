package com.henfon.shop.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.dto.SysUserCreateRequest;
import com.henfon.shop.identity.dto.SysUserUpdateRequest;
import com.henfon.shop.identity.entity.SysUser;
import com.henfon.shop.identity.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 后台用户应用服务。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Service
public class SysUserService {

    private static final long MAX_PAGE_SIZE = 200L;

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 创建后台用户服务。
     *
     * @param sysUserMapper 系统用户数据访问对象
     * @param passwordEncoder 密码编码器
     * @author Henfon
     * @date 2026-08-29
     */
    public SysUserService(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 分页查询后台用户。
     *
     * @param tenantId 租户/组织ID
     * @param keyword 用户名、姓名或手机号关键字
     * @param deptId 部门ID
     * @param status 状态
     * @param current 当前页
     * @param size 页大小
     * @return 用户分页数据
     * @author Henfon
     * @date 2026-08-29
     */
    public IPage<SysUser> page(Long tenantId, String keyword, Long deptId, Integer status,
                               long current, long size) {
        // 查询统一限定未删除数据，数据范围过滤将在认证上下文建立后补充。
        long safeCurrent = Math.max(current, 1L);
        long safeSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(tenantId != null, SysUser::getTenantId, tenantId)
                .eq(deptId != null, SysUser::getDeptId, deptId)
                .eq(status != null, SysUser::getStatus, status)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(SysUser::getUsername, keyword)
                        .or().like(SysUser::getRealName, keyword)
                        .or().like(SysUser::getPhone, keyword))
                .orderByDesc(SysUser::getCreatedAt);
        return sysUserMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
    }

    /**
     * 创建后台用户。
     *
     * @param request 创建请求
     * @return 新用户ID
     * @author Henfon
     * @date 2026-08-29
     */
    public Long create(SysUserCreateRequest request) {
        Long tenantId = request.tenantId() == null ? 0L : request.tenantId();
        long duplicateCount = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getUsername, request.username()));
        if (duplicateCount > 0) {
            throw new BusinessException("USER_EXISTS", "登录用户名已存在");
        }
        SysUser user = new SysUser();
        user.setTenantId(tenantId);
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRealName(request.realName());
        user.setNickname(request.nickname());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        user.setAvatarUrl(request.avatarUrl());
        user.setDeptId(request.deptId());
        user.setStatus(request.status() == null ? 1 : request.status());
        user.setUserType("ADMIN");
        user.setRemark(request.remark());
        sysUserMapper.insert(user);
        return user.getId();
    }

    /**
     * 修改后台用户资料。
     *
     * @param id 用户ID
     * @param request 修改请求
     * @author Henfon
     * @date 2026-08-29
     */
    public void update(Long id, SysUserUpdateRequest request) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "系统用户不存在");
        }
        user.setRealName(request.realName());
        user.setNickname(request.nickname());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        user.setAvatarUrl(request.avatarUrl());
        user.setDeptId(request.deptId());
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        user.setRemark(request.remark());
        sysUserMapper.updateById(user);
    }

    /**
     * 逻辑删除后台用户。
     *
     * @param id 用户ID
     * @author Henfon
     * @date 2026-08-29
     */
    public void delete(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "系统用户不存在");
        }
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            throw new BusinessException("USER_PROTECTED", "主管理员账号不可删除");
        }
        sysUserMapper.deleteById(id);
    }
}
