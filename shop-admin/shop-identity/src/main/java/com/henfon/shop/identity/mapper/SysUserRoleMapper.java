package com.henfon.shop.identity.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.henfon.shop.identity.entity.SysMenu;

import java.util.List;

/**
 * 系统用户角色关联数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Mapper
public interface SysUserRoleMapper {

    /**
     * 查询用户角色ID。
     *
     * @param userId 用户ID
     * @return 角色ID列表
     * @author Henfon
     * @date 2026-08-29
     */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIds(@Param("userId") Long userId);

    /**
     * 查询用户拥有的菜单权限编码。
     *
     * @param userId 用户ID
     * @return 权限编码列表
     * @author Henfon
     * @date 2026-08-29
     */
    @Select("SELECT DISTINCT m.permission_code FROM sys_user_role ur "
            + "JOIN sys_role r ON r.id = ur.role_id AND r.status = 1 AND r.is_deleted = 0 "
            + "JOIN sys_role_menu rm ON rm.role_id = r.id "
            + "JOIN sys_menu m ON m.id = rm.menu_id AND m.status = 1 AND m.is_deleted = 0 "
            + "WHERE ur.user_id = #{userId} AND m.permission_code IS NOT NULL AND m.permission_code <> ''")
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    /**
     * 按权限编码反查拥有该权限的用户。
     *
     * 用于回答"谁能做这件事"，而不是"某人能做什么"：排班、待办分派这类场景要先列出候选
     * 人员，按角色名去匹配既脆弱（角色可改名）又漏人（同一权限可以由多个角色持有），
     * 按权限编码反查得到的就是系统判定的全部人选。
     *
     * @param permissionCode 权限编码
     * @return 用户ID列表
     * @author Henfon
     * @date 2026-09-21
     */
    @Select("SELECT DISTINCT ur.user_id FROM sys_user_role ur "
            + "JOIN sys_role r ON r.id = ur.role_id AND r.status = 1 AND r.is_deleted = 0 "
            + "JOIN sys_role_menu rm ON rm.role_id = r.id "
            + "JOIN sys_menu m ON m.id = rm.menu_id AND m.status = 1 AND m.is_deleted = 0 "
            + "WHERE m.permission_code = #{permissionCode}")
    List<Long> selectUserIdsByPermission(@Param("permissionCode") String permissionCode);

    /**
     * 查询用户可见的菜单树节点。
     *
     * @param userId 用户ID
     * @return 菜单节点列表
     * @author Henfon
     * @date 2026-08-29
     */
    @Select("WITH RECURSIVE authorized_menu AS ("
            + "SELECT DISTINCT m.id,m.parent_id,m.menu_name,m.menu_type,m.route_path,m.component,m.icon,"
            + "m.permission_code,m.sort_no,m.visible,m.status,m.keep_alive,m.external_url,m.created_at,"
            + "m.updated_at,m.is_deleted,m.version,m.remark FROM sys_user_role ur "
            + "JOIN sys_role r ON r.id = ur.role_id AND r.status = 1 AND r.is_deleted = 0 "
            + "JOIN sys_role_menu rm ON rm.role_id = r.id "
            + "JOIN sys_menu m ON m.id = rm.menu_id AND m.status = 1 AND m.visible = 1 AND m.is_deleted = 0 "
            + "WHERE ur.user_id = #{userId} "
            + "UNION "
            + "SELECT parent.id,parent.parent_id,parent.menu_name,parent.menu_type,parent.route_path,parent.component,parent.icon,"
            + "parent.permission_code,parent.sort_no,parent.visible,parent.status,parent.keep_alive,parent.external_url,parent.created_at,"
            + "parent.updated_at,parent.is_deleted,parent.version,parent.remark FROM sys_menu parent "
            + "JOIN authorized_menu child ON child.parent_id = parent.id "
            + "WHERE parent.status = 1 AND parent.visible = 1 AND parent.is_deleted = 0"
            + ") SELECT id,parent_id,menu_name,menu_type,route_path,component,icon,permission_code,sort_no,visible,status,"
            + "keep_alive,external_url,created_at,updated_at,is_deleted,version,remark FROM authorized_menu "
            + "ORDER BY parent_id,sort_no,id")
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);

    /**
     * 删除用户的全部角色关系。
     *
     * @param userId 用户ID
     * @return 删除行数
     * @author Henfon
     * @date 2026-08-29
     */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 新增用户角色关系。
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 新增行数
     * @author Henfon
     * @date 2026-08-29
     */
    @Insert("INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insertRelation(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
