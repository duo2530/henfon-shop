package com.henfon.shop.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.entity.SysDataRule;
import com.henfon.shop.identity.entity.SysDept;
import com.henfon.shop.identity.entity.SysMenu;
import com.henfon.shop.identity.entity.SysRole;
import com.henfon.shop.identity.mapper.SysDataRuleMapper;
import com.henfon.shop.identity.mapper.SysDeptMapper;
import com.henfon.shop.identity.mapper.SysMenuMapper;
import com.henfon.shop.identity.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 系统角色、菜单、部门和数据规则应用服务。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Service
public class IdentityMetadataService {

    private final SysDeptMapper sysDeptMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysDataRuleMapper sysDataRuleMapper;

    /**
     * 创建身份元数据服务。
     *
     * @param sysDeptMapper 部门数据访问对象
     * @param sysRoleMapper 角色数据访问对象
     * @param sysMenuMapper 菜单数据访问对象
     * @param sysDataRuleMapper 数据规则数据访问对象
     * @author Henfon
     * @date 2026-08-29
     */
    public IdentityMetadataService(SysDeptMapper sysDeptMapper, SysRoleMapper sysRoleMapper,
                                   SysMenuMapper sysMenuMapper, SysDataRuleMapper sysDataRuleMapper) {
        this.sysDeptMapper = sysDeptMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.sysDataRuleMapper = sysDataRuleMapper;
    }

    /**
     * 查询部门列表。
     *
     * @param tenantId 租户/组织ID
     * @return 部门列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<SysDept> listDepts(Long tenantId) {
        return sysDeptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .eq(tenantId != null, SysDept::getTenantId, tenantId)
                .orderByAsc(SysDept::getSortNo));
    }

    /**
     * 保存部门。
     *
     * @param dept 部门实体
     * @return 部门ID
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public Long saveDept(SysDept dept) {
        if (!StringUtils.hasText(dept.getDeptName()) || !StringUtils.hasText(dept.getDeptCode())) {
            throw new BusinessException("DEPT_INVALID", "部门名称和编码不能为空");
        }
        if (dept.getTenantId() == null) {
            dept.setTenantId(0L);
        }
        if (dept.getParentId() == null) {
            dept.setParentId(0L);
        }
        if (dept.getStatus() == null) {
            dept.setStatus(1);
        }
        if (dept.getId() == null) {
            sysDeptMapper.insert(dept);
        } else {
            ensureDeptExists(dept.getId());
            sysDeptMapper.updateById(dept);
        }
        return dept.getId();
    }

    /**
     * 查询角色列表。
     *
     * @param tenantId 租户/组织ID
     * @return 角色列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<SysRole> listRoles(Long tenantId) {
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(tenantId != null, SysRole::getTenantId, tenantId)
                .orderByAsc(SysRole::getRoleSort));
    }

    /**
     * 保存角色。
     *
     * @param role 角色实体
     * @return 角色ID
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public Long saveRole(SysRole role) {
        if (!StringUtils.hasText(role.getRoleKey()) || !StringUtils.hasText(role.getRoleName())) {
            throw new BusinessException("ROLE_INVALID", "角色标识和名称不能为空");
        }
        if (role.getTenantId() == null) {
            role.setTenantId(0L);
        }
        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        if (!StringUtils.hasText(role.getDataScope())) {
            role.setDataScope("SELF");
        }
        if (role.getId() == null) {
            sysRoleMapper.insert(role);
        } else {
            ensureRoleExists(role.getId());
            sysRoleMapper.updateById(role);
        }
        return role.getId();
    }

    /**
     * 删除角色。
     *
     * @param id 角色ID
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public void deleteRole(Long id) {
        // 超级管理员角色由系统保留，避免误删导致管理端失去入口。
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        }
        if ("SUPER_ADMIN".equalsIgnoreCase(role.getRoleKey())) {
            throw new BusinessException("ROLE_PROTECTED", "超级管理员角色不可删除");
        }
        sysRoleMapper.deleteById(id);
    }

    /**
     * 查询菜单权限列表。
     *
     * @return 菜单和按钮列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<SysMenu> listMenus() {
        return sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getSortNo));
    }

    /**
     * 保存菜单或按钮权限。
     *
     * @param menu 菜单实体
     * @return 菜单ID
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public Long saveMenu(SysMenu menu) {
        if (!StringUtils.hasText(menu.getMenuName()) || !StringUtils.hasText(menu.getMenuType())) {
            throw new BusinessException("MENU_INVALID", "菜单名称和类型不能为空");
        }
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        if (menu.getStatus() == null) {
            menu.setStatus(1);
        }
        if (menu.getVisible() == null) {
            menu.setVisible(1);
        }
        if (menu.getId() == null) {
            sysMenuMapper.insert(menu);
        } else {
            ensureMenuExists(menu.getId());
            sysMenuMapper.updateById(menu);
        }
        return menu.getId();
    }

    /**
     * 删除菜单或按钮权限。
     *
     * @param id 菜单或按钮ID
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public void deleteMenu(Long id) {
        // 使用 MyBatis-Plus 逻辑删除，避免破坏历史授权关系审计数据。
        ensureMenuExists(id);
        sysMenuMapper.deleteById(id);
    }

    /**
     * 查询数据权限规则。
     *
     * @param tenantId 租户/组织ID
     * @return 数据规则列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<SysDataRule> listDataRules(Long tenantId) {
        return sysDataRuleMapper.selectList(new LambdaQueryWrapper<SysDataRule>()
                .eq(tenantId != null, SysDataRule::getTenantId, tenantId)
                .orderByDesc(SysDataRule::getUpdatedAt));
    }

    /**
     * 保存数据权限规则。
     *
     * @param rule 数据规则实体
     * @return 规则ID
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public Long saveDataRule(SysDataRule rule) {
        if (!StringUtils.hasText(rule.getRuleName()) || !StringUtils.hasText(rule.getModuleKey())
                || !StringUtils.hasText(rule.getScopeType())) {
            throw new BusinessException("DATA_RULE_INVALID", "规则名称、模块和范围类型不能为空");
        }
        if (rule.getTenantId() == null) {
            rule.setTenantId(0L);
        }
        if (rule.getStatus() == null) {
            rule.setStatus(1);
        }
        if (rule.getId() == null) {
            sysDataRuleMapper.insert(rule);
        } else {
            ensureDataRuleExists(rule.getId());
            sysDataRuleMapper.updateById(rule);
        }
        return rule.getId();
    }

    /**
     * 删除数据权限规则。
     *
     * @param id 规则ID
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public void deleteDataRule(Long id) {
        // 采用逻辑删除保留规则变更历史，关联关系由数据库级逻辑记录继续保留。
        ensureDataRuleExists(id);
        sysDataRuleMapper.deleteById(id);
    }

    /**
     * 删除指定部门前校验部门存在。
     *
     * @param id 部门ID
     * @author Henfon
     * @date 2026-08-29
     */
    private void ensureDeptExists(Long id) {
        if (sysDeptMapper.selectById(id) == null) {
            throw new BusinessException("DEPT_NOT_FOUND", "部门不存在");
        }
    }

    /**
     * 删除指定角色前校验角色存在。
     *
     * @param id 角色ID
     * @author Henfon
     * @date 2026-08-29
     */
    private void ensureRoleExists(Long id) {
        if (sysRoleMapper.selectById(id) == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        }
    }

    /**
     * 修改指定菜单前校验菜单存在。
     *
     * @param id 菜单ID
     * @author Henfon
     * @date 2026-08-29
     */
    private void ensureMenuExists(Long id) {
        if (sysMenuMapper.selectById(id) == null) {
            throw new BusinessException("MENU_NOT_FOUND", "菜单不存在");
        }
    }

    /**
     * 修改指定数据规则前校验规则存在。
     *
     * @param id 规则ID
     * @author Henfon
     * @date 2026-08-29
     */
    private void ensureDataRuleExists(Long id) {
        if (sysDataRuleMapper.selectById(id) == null) {
            throw new BusinessException("DATA_RULE_NOT_FOUND", "数据权限规则不存在");
        }
    }
}
