package com.henfon.shop.export.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导出任务实体。
 *
 * @author Henfon
 * @date 2026-09-16
 */
@Data
@TableName("export_task")
public class ExportTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编号，插入后由主键回填，形如 EXP20260916-0001。 */
    private String taskNo;

    /** 导出类型，取 ExportType 枚举名。 */
    private String exportType;

    /** 导出名称，用于列表展示与文件名。 */
    private String exportName;

    /** 提交时的筛选条件 JSON 快照。 */
    private String queryParams;

    /** 任务状态，取 ExportTaskStatus 枚举名。 */
    private String status;

    /** 生成的文件名。 */
    private String fileName;

    /** Excel 文件在 MinIO 的对象键。 */
    private String objectKey;

    /** 文件大小，单位字节。 */
    private Long fileSize;

    /** 导出数据行数，不含表头。 */
    private Integer rowCount;

    /** 失败原因。 */
    private String errorMessage;

    /** 提交人管理员ID。 */
    private Long requestedBy;

    /** 提交人名称快照。 */
    private String requestedByName;

    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /** 最近一次被认领执行的时间，用于识别超时未完成的僵尸任务。 */
    private LocalDateTime claimedAt;

    /** 文件过期时间。 */
    private LocalDateTime expiresAt;

    /** 提交人是否已移除该记录：1已移除，0正常。 */
    private Integer isDeleted;
}
