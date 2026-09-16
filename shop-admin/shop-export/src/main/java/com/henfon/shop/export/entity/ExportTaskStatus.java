package com.henfon.shop.export.entity;

/**
 * 导出任务状态。
 *
 * <p>状态流转固定为 PENDING → RUNNING → SUCCESS / FAILED，成功后到期再转 EXPIRED。
 * 只有 PENDING 可以被认领执行，认领动作通过带状态条件的更新完成，因此
 * RocketMQ 重复投递或兜底扫描并发触发时，同一个任务只会被一个执行者拿到。</p>
 *
 * @author Henfon
 * @date 2026-09-16
 */
public enum ExportTaskStatus {

    /** 已提交，等待执行。 */
    PENDING,

    /** 正在生成文件。 */
    RUNNING,

    /** 生成成功，文件可下载。 */
    SUCCESS,

    /** 生成失败，可重试。 */
    FAILED,

    /** 文件已到期清理，仅保留记录。 */
    EXPIRED;

    /**
     * 判断文本是否为本枚举内的合法状态。
     *
     * @param value 待判断文本
     * @return 是否为合法状态
     * @author Henfon
     * @date 2026-09-16
     */
    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (ExportTaskStatus status : values()) {
            if (status.name().equals(value.trim().toUpperCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
