package com.henfon.shop.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 行级、列级数据权限规则实体。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Data
@TableName("sys_data_rule")
public class SysDataRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String ruleName;
    private String moduleKey;
    private String scopeType;
    private String customDeptIds;
    private String fieldMasks;
    private String filterExpression;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
    private String remark;
}
