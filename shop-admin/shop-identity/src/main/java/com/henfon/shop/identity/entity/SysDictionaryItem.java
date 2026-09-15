package com.henfon.shop.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统字典项实体。
 *
 * @author Henfon
 * @date 2026-09-15
 */
@Data
@TableName("sys_dictionary_item")
public class SysDictionaryItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String dictType;
    private String itemCode;
    private String itemName;
    private Integer sortNo;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    private String remark;
}
