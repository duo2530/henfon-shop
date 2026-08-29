package com.henfon.shop.identity.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统角色数据规则关联数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Mapper
public interface SysRoleDataRuleMapper {

    /**
     * 查询角色数据规则ID。
     *
     * @param roleId 角色ID
     * @return 规则ID列表
     * @author Henfon
     * @date 2026-08-29
     */
    @Select("SELECT rule_id FROM sys_role_data_rule WHERE role_id = #{roleId}")
    List<Long> selectRuleIds(@Param("roleId") Long roleId);

    /**
     * 删除角色的全部数据规则关系。
     *
     * @param roleId 角色ID
     * @return 删除行数
     * @author Henfon
     * @date 2026-08-29
     */
    @Delete("DELETE FROM sys_role_data_rule WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 新增角色数据规则关系。
     *
     * @param roleId 角色ID
     * @param ruleId 数据规则ID
     * @return 新增行数
     * @author Henfon
     * @date 2026-08-29
     */
    @Insert("INSERT IGNORE INTO sys_role_data_rule (role_id, rule_id) VALUES (#{roleId}, #{ruleId})")
    int insertRelation(@Param("roleId") Long roleId, @Param("ruleId") Long ruleId);
}
