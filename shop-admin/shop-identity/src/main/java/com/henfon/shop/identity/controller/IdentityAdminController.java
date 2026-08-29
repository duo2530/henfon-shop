package com.henfon.shop.identity.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.identity.dto.SysUserCreateRequest;
import com.henfon.shop.identity.dto.SysUserUpdateRequest;
import com.henfon.shop.identity.entity.SysDataRule;
import com.henfon.shop.identity.entity.SysDept;
import com.henfon.shop.identity.entity.SysMenu;
import com.henfon.shop.identity.entity.SysRole;
import com.henfon.shop.identity.entity.SysUser;
import com.henfon.shop.identity.service.IdentityMetadataService;
import com.henfon.shop.identity.service.PermissionAssignmentService;
import com.henfon.shop.identity.service.SysUserService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台身份与权限基础接口。
 *
 * <p>接口路径与现有管理端的系统用户、角色、菜单、数据权限页面对应，所有接口均通过 JWT 与菜单权限编码鉴权。</p>
 *
 * @author Henfon
 * @date 2026-08-29
 */
@RestController
@RequestMapping("/api/admin/system")
public class IdentityAdminController {

    private final SysUserService sysUserService;
    private final IdentityMetadataService identityMetadataService;
    private final PermissionAssignmentService permissionAssignmentService;

    /**
     * 创建身份权限控制器。
     *
     * @param sysUserService 系统用户服务
     * @param identityMetadataService 身份元数据服务
     * @param permissionAssignmentService 权限关系服务
     * @author Henfon
     * @date 2026-08-29
     */
    public IdentityAdminController(SysUserService sysUserService,
                                   IdentityMetadataService identityMetadataService,
                                   PermissionAssignmentService permissionAssignmentService) {
        this.sysUserService = sysUserService;
        this.identityMetadataService = identityMetadataService;
        this.permissionAssignmentService = permissionAssignmentService;
    }

    /**
     * 分页查询系统用户。
     *
     * @param tenantId 租户/组织ID
     * @param keyword 用户名、姓名或手机号关键字
     * @param deptId 部门ID
     * @param status 状态
     * @param current 当前页
     * @param size 页大小
     * @return 系统用户分页结果
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('system:user:query')")
    public ApiResponse<IPage<SysUser>> pageUsers(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(sysUserService.page(tenantId, keyword, deptId, status, current, size), requestId());
    }

    /**
     * 创建系统用户。
     *
     * @param request 创建请求
     * @return 新用户ID
     * @author Henfon
     * @date 2026-08-29
     */
    @PostMapping("/users")
    @PreAuthorize("hasAuthority('system:user:add')")
    public ApiResponse<Long> createUser(@Valid @RequestBody SysUserCreateRequest request) {
        return ApiResponse.success(sysUserService.create(request), requestId());
    }

    /**
     * 修改系统用户。
     *
     * @param id 用户ID
     * @param request 修改请求
     * @return 空响应
     * @author Henfon
     * @date 2026-08-29
     */
    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    public ApiResponse<Void> updateUser(@PathVariable Long id,
                                         @Valid @RequestBody SysUserUpdateRequest request) {
        sysUserService.update(id, request);
        return ApiResponse.success(requestId());
    }

    /**
     * 删除系统用户。
     *
     * @param id 用户ID
     * @return 空响应
     * @author Henfon
     * @date 2026-08-29
     */
    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        sysUserService.delete(id);
        return ApiResponse.success(requestId());
    }

    /**
     * 查询部门列表。
     *
     * @param tenantId 租户/组织ID
     * @return 部门列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/depts")
    @PreAuthorize("hasAuthority('system:dept:query')")
    public ApiResponse<List<SysDept>> listDepts(@RequestParam(required = false) Long tenantId) {
        return ApiResponse.success(identityMetadataService.listDepts(tenantId), requestId());
    }

    /**
     * 保存部门。
     *
     * @param dept 部门实体
     * @return 部门ID
     * @author Henfon
     * @date 2026-08-29
     */
    @PostMapping("/depts")
    @PreAuthorize("hasAuthority('system:dept:save')")
    public ApiResponse<Long> saveDept(@RequestBody SysDept dept) {
        return ApiResponse.success(identityMetadataService.saveDept(dept), requestId());
    }

    /**
     * 查询角色列表。
     *
     * @param tenantId 租户/组织ID
     * @return 角色列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('system:role:query')")
    public ApiResponse<List<SysRole>> listRoles(@RequestParam(required = false) Long tenantId) {
        return ApiResponse.success(identityMetadataService.listRoles(tenantId), requestId());
    }

    /**
     * 保存角色。
     *
     * @param role 角色实体
     * @return 角色ID
     * @author Henfon
     * @date 2026-08-29
     */
    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('system:role:save')")
    public ApiResponse<Long> saveRole(@RequestBody SysRole role) {
        return ApiResponse.success(identityMetadataService.saveRole(role), requestId());
    }

    /**
     * 删除角色。
     *
     * @param id 角色ID
     * @return 空响应
     * @author Henfon
     * @date 2026-08-29
     */
    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('system:role:save')")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        // 由应用服务统一执行保护角色校验和逻辑删除。
        identityMetadataService.deleteRole(id);
        return ApiResponse.success(requestId());
    }

    /**
     * 查询菜单和按钮权限列表。
     *
     * @return 菜单权限列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/menus")
    @PreAuthorize("hasAuthority('system:menu:query')")
    public ApiResponse<List<SysMenu>> listMenus() {
        return ApiResponse.success(identityMetadataService.listMenus(), requestId());
    }

    /**
     * 保存菜单或按钮权限。
     *
     * @param menu 菜单实体
     * @return 菜单ID
     * @author Henfon
     * @date 2026-08-29
     */
    @PostMapping("/menus")
    @PreAuthorize("hasAuthority('system:menu:save')")
    public ApiResponse<Long> saveMenu(@RequestBody SysMenu menu) {
        return ApiResponse.success(identityMetadataService.saveMenu(menu), requestId());
    }

    /**
     * 删除菜单或按钮权限。
     *
     * @param id 菜单或按钮ID
     * @return 空响应
     * @author Henfon
     * @date 2026-08-29
     */
    @DeleteMapping("/menus/{id}")
    @PreAuthorize("hasAuthority('system:menu:save')")
    public ApiResponse<Void> deleteMenu(@PathVariable Long id) {
        // 菜单删除与授权关系校验集中在身份元数据服务中处理。
        identityMetadataService.deleteMenu(id);
        return ApiResponse.success(requestId());
    }

    /**
     * 查询数据权限规则。
     *
     * @param tenantId 租户/组织ID
     * @return 数据权限规则列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/data-rules")
    @PreAuthorize("hasAuthority('system:data-rule:query')")
    public ApiResponse<List<SysDataRule>> listDataRules(@RequestParam(required = false) Long tenantId) {
        return ApiResponse.success(identityMetadataService.listDataRules(tenantId), requestId());
    }

    /**
     * 保存数据权限规则。
     *
     * @param rule 数据权限规则
     * @return 规则ID
     * @author Henfon
     * @date 2026-08-29
     */
    @PostMapping("/data-rules")
    @PreAuthorize("hasAuthority('system:data-rule:save')")
    public ApiResponse<Long> saveDataRule(@RequestBody SysDataRule rule) {
        return ApiResponse.success(identityMetadataService.saveDataRule(rule), requestId());
    }

    /**
     * 删除数据权限规则。
     *
     * @param id 规则ID
     * @return 空响应
     * @author Henfon
     * @date 2026-08-29
     */
    @DeleteMapping("/data-rules/{id}")
    @PreAuthorize("hasAuthority('system:data-rule:save')")
    public ApiResponse<Void> deleteDataRule(@PathVariable Long id) {
        // 使用逻辑删除，避免直接暴露数据库删除细节。
        identityMetadataService.deleteDataRule(id);
        return ApiResponse.success(requestId());
    }

    /**
     * 查询用户角色关系。
     *
     * @param userId 用户ID
     * @return 角色ID列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('system:user-role:query')")
    public ApiResponse<List<Long>> listUserRoleIds(@PathVariable Long userId) {
        return ApiResponse.success(permissionAssignmentService.listUserRoleIds(userId), requestId());
    }

    /**
     * 替换用户角色关系。
     *
     * @param userId 用户ID
     * @param roleIds 角色ID列表
     * @return 空响应
     * @author Henfon
     * @date 2026-08-29
     */
    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('system:user-role:save')")
    public ApiResponse<Void> replaceUserRoles(@PathVariable Long userId,
                                              @RequestBody List<Long> roleIds) {
        permissionAssignmentService.replaceUserRoles(userId, roleIds);
        return ApiResponse.success(requestId());
    }

    /**
     * 查询角色菜单权限关系。
     *
     * @param roleId 角色ID
     * @return 菜单ID列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/roles/{roleId}/menus")
    @PreAuthorize("hasAuthority('system:role-menu:query')")
    public ApiResponse<List<Long>> listRoleMenuIds(@PathVariable Long roleId) {
        return ApiResponse.success(permissionAssignmentService.listRoleMenuIds(roleId), requestId());
    }

    /**
     * 替换角色菜单权限关系。
     *
     * @param roleId 角色ID
     * @param menuIds 菜单和按钮ID列表
     * @return 空响应
     * @author Henfon
     * @date 2026-08-29
     */
    @PutMapping("/roles/{roleId}/menus")
    @PreAuthorize("hasAuthority('system:role-menu:save')")
    public ApiResponse<Void> replaceRoleMenus(@PathVariable Long roleId,
                                              @RequestBody List<Long> menuIds) {
        permissionAssignmentService.replaceRoleMenus(roleId, menuIds);
        return ApiResponse.success(requestId());
    }

    /**
     * 查询角色数据权限关系。
     *
     * @param roleId 角色ID
     * @return 数据规则ID列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/roles/{roleId}/data-rules")
    @PreAuthorize("hasAuthority('system:role-data-rule:query')")
    public ApiResponse<List<Long>> listRoleDataRuleIds(@PathVariable Long roleId) {
        return ApiResponse.success(permissionAssignmentService.listRoleDataRuleIds(roleId), requestId());
    }

    /**
     * 替换角色数据权限关系。
     *
     * @param roleId 角色ID
     * @param ruleIds 数据规则ID列表
     * @return 空响应
     * @author Henfon
     * @date 2026-08-29
     */
    @PutMapping("/roles/{roleId}/data-rules")
    @PreAuthorize("hasAuthority('system:role-data-rule:save')")
    public ApiResponse<Void> replaceRoleDataRules(@PathVariable Long roleId,
                                                  @RequestBody List<Long> ruleIds) {
        permissionAssignmentService.replaceRoleDataRules(roleId, ruleIds);
        return ApiResponse.success(requestId());
    }

    /**
     * 获取当前请求链路标识。
     *
     * @return 请求链路标识
     * @author Henfon
     * @date 2026-08-29
     */
    private String requestId() {
        return MDC.get("requestId");
    }
}
