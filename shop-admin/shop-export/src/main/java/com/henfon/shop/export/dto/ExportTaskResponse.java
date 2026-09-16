package com.henfon.shop.export.dto;

import java.time.LocalDateTime;

/**
 * 导出任务响应，供顶栏下载中心列表展示。
 *
 * @param id 任务ID
 * @param taskNo 任务编号
 * @param exportType 导出类型
 * @param exportName 导出名称
 * @param status 任务状态
 * @param fileName 文件名
 * @param fileSize 文件大小，单位字节
 * @param rowCount 数据行数
 * @param errorMessage 失败原因
 * @param requestedByName 提交人名称
 * @param createdAt 提交时间
 * @param finishedAt 完成时间
 * @param expiresAt 文件过期时间
 * @param downloadable 当前是否可下载
 * @author Henfon
 * @date 2026-09-16
 */
public record ExportTaskResponse(Long id,
                                 String taskNo,
                                 String exportType,
                                 String exportName,
                                 String status,
                                 String fileName,
                                 Long fileSize,
                                 Integer rowCount,
                                 String errorMessage,
                                 String requestedByName,
                                 LocalDateTime createdAt,
                                 LocalDateTime finishedAt,
                                 LocalDateTime expiresAt,
                                 boolean downloadable) {
}
