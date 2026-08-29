package com.henfon.shop.identity.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统角色菜单关联数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Mapper
public interface SysRoleMenuMapper {

    /**
     * 查询角色菜单ID。
     *
     * @param roleId 角色ID
     * @return 菜单ID列表
     * @author Henfon
     * @date 2026-08-29
     */
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIds(@Param("roleId") Long roleId);

    /**
     * 删除角色的全部菜单关系。
     *
     * @param roleId 角色ID
     * @return 删除行数
     * @author Henfon
     * @date 2026-08-29
     */
    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 新增角色菜单关系。
     *
     * @param roleId 角色ID
     * @param menuId 菜单ID
     * @return 新增行数
     * @author Henfon
     * @date 2026-08-29
     */
    @Insert("INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (#{roleId}, #{menuId})")
    int insertRelation(@Param("roleId") Long roleId, @Param("menuId") Long menuId);
}
