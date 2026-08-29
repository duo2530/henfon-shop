package com.henfon.shop.identity.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.mapper.SysRoleDataRuleMapper;
import com.henfon.shop.identity.mapper.SysRoleMapper;
import com.henfon.shop.identity.mapper.SysRoleMenuMapper;
import com.henfon.shop.identity.mapper.SysUserMapper;
import com.henfon.shop.identity.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 用户、角色、菜单和数据规则关系维护服务。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Service
public class PermissionAssignmentService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysRoleDataRuleMapper sysRoleDataRuleMapper;

    /**
     * 创建权限关系服务。
     *
     * @param sysUserMapper 用户数据访问对象
     * @param sysRoleMapper 角色数据访问对象
     * @param sysUserRoleMapper 用户角色关联数据访问对象
     * @param sysRoleMenuMapper 角色菜单关联数据访问对象
     * @param sysRoleDataRuleMapper 角色数据规则关联数据访问对象
     * @author Henfon
     * @date 2026-08-29
     */
    public PermissionAssignmentService(SysUserMapper sysUserMapper, SysRoleMapper sysRoleMapper,
                                       SysUserRoleMapper sysUserRoleMapper, SysRoleMenuMapper sysRoleMenuMapper,
                                       SysRoleDataRuleMapper sysRoleDataRuleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.sysRoleDataRuleMapper = sysRoleDataRuleMapper;
    }

    /**
     * 查询用户已分配的角色。
     *
     * @param userId 用户ID
     * @return 角色ID列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<Long> listUserRoleIds(Long userId) {
        ensureUserExists(userId);
        return sysUserRoleMapper.selectRoleIds(userId);
    }

    /**
     * 替换用户角色关系。
     *
     * @param userId 用户ID
     * @param roleIds 角色ID列表
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public void replaceUserRoles(Long userId, List<Long> roleIds) {
        ensureUserExists(userId);
        List<Long> safeRoleIds = roleIds == null ? Collections.emptyList() : roleIds;
        safeRoleIds.forEach(this::ensureRoleExists);
        sysUserRoleMapper.deleteByUserId(userId);
        safeRoleIds.forEach(roleId -> sysUserRoleMapper.insertRelation(userId, roleId));
    }

    /**
     * 查询角色已分配的菜单权限。
     *
     * @param roleId 角色ID
     * @return 菜单ID列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<Long> listRoleMenuIds(Long roleId) {
        ensureRoleExists(roleId);
        return sysRoleMenuMapper.selectMenuIds(roleId);
    }

    /**
     * 替换角色菜单权限关系。
     *
     * @param roleId 角色ID
     * @param menuIds 菜单和按钮ID列表
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public void replaceRoleMenus(Long roleId, List<Long> menuIds) {
        ensureRoleExists(roleId);
        List<Long> safeMenuIds = menuIds == null ? Collections.emptyList() : menuIds;
        sysRoleMenuMapper.deleteByRoleId(roleId);
        safeMenuIds.forEach(menuId -> sysRoleMenuMapper.insertRelation(roleId, menuId));
    }

    /**
     * 查询角色已分配的数据规则。
     *
     * @param roleId 角色ID
     * @return 数据规则ID列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<Long> listRoleDataRuleIds(Long roleId) {
        ensureRoleExists(roleId);
        return sysRoleDataRuleMapper.selectRuleIds(roleId);
    }

    /**
     * 替换角色数据权限规则关系。
     *
     * @param roleId 角色ID
     * @param ruleIds 数据规则ID列表
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public void replaceRoleDataRules(Long roleId, List<Long> ruleIds) {
        ensureRoleExists(roleId);
        List<Long> safeRuleIds = ruleIds == null ? Collections.emptyList() : ruleIds;
        sysRoleDataRuleMapper.deleteByRoleId(roleId);
        safeRuleIds.forEach(ruleId -> sysRoleDataRuleMapper.insertRelation(roleId, ruleId));
    }

    /**
     * 校验用户存在。
     *
     * @param userId 用户ID
     * @author Henfon
     * @date 2026-08-29
     */
    private void ensureUserExists(Long userId) {
        if (userId == null || sysUserMapper.selectById(userId) == null) {
            throw new BusinessException("USER_NOT_FOUND", "系统用户不存在");
        }
    }

    /**
     * 校验角色存在。
     *
     * @param roleId 角色ID
     * @author Henfon
     * @date 2026-08-29
     */
    private void ensureRoleExists(Long roleId) {
        if (roleId == null || sysRoleMapper.selectById(roleId) == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "系统角色不存在");
        }
    }
}
