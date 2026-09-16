package com.henfon.shop.export.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.export.entity.ExportTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 导出任务数据访问接口。
 *
 * <p>状态流转全部使用带前置状态条件的更新语句，靠数据库的行级原子性保证
 * 同一个任务只被认领一次，RocketMQ 的重复投递与兜底扫描并发触发都不会重复生成。</p>
 *
 * @author Henfon
 * @date 2026-09-16
 */
@Mapper
public interface ExportTaskMapper extends BaseMapper<ExportTask> {

    /**
     * 认领任务：仅当任务仍处于 PENDING 时置为 RUNNING。
     *
     * @param id 任务ID
     * @param now 认领时间
     * @return 影响行数，1 表示认领成功
     * @author Henfon
     * @date 2026-09-16
     */
    @Update("UPDATE export_task SET status = 'RUNNING', started_at = #{now}, claimed_at = #{now}, "
            + "error_message = NULL WHERE id = #{id} AND status = 'PENDING'")
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 标记任务生成成功并记录文件产物信息。
     *
     * @param id 任务ID
     * @param objectKey MinIO 对象键
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @param rowCount 数据行数
     * @param now 完成时间
     * @param expiresAt 文件过期时间
     * @param claimedAt 本次认领时刻，用于识别已被兜底调度重新认领的过期执行者
     * @return 影响行数，0 表示本次结果已被更新的认领覆盖
     * @author Henfon
     * @date 2026-09-16
     */
    @Update("UPDATE export_task SET status = 'SUCCESS', object_key = #{objectKey}, file_name = #{fileName}, "
            + "file_size = #{fileSize}, row_count = #{rowCount}, finished_at = #{now}, expires_at = #{expiresAt}, "
            + "error_message = NULL WHERE id = #{id} AND status = 'RUNNING' AND claimed_at = #{claimedAt}")
    int markSuccess(@Param("id") Long id,
                    @Param("objectKey") String objectKey,
                    @Param("fileName") String fileName,
                    @Param("fileSize") Long fileSize,
                    @Param("rowCount") Integer rowCount,
                    @Param("now") LocalDateTime now,
                    @Param("expiresAt") LocalDateTime expiresAt,
                    @Param("claimedAt") LocalDateTime claimedAt);

    /**
     * 标记任务生成失败。
     *
     * @param id 任务ID
     * @param message 失败原因
     * @param now 完成时间
     * @param claimedAt 本次认领时刻，避免过期执行者覆盖新一次认领的状态
     * @return 影响行数
     * @author Henfon
     * @date 2026-09-16
     */
    @Update("UPDATE export_task SET status = 'FAILED', error_message = #{message}, finished_at = #{now} "
            + "WHERE id = #{id} AND status = 'RUNNING' AND claimed_at = #{claimedAt}")
    int markFailed(@Param("id") Long id, @Param("message") String message, @Param("now") LocalDateTime now,
                   @Param("claimedAt") LocalDateTime claimedAt);

    /**
     * 回收僵尸任务：将长时间处于 RUNNING 的任务重新置为待执行。
     *
     * <p>通常由进程重启导致执行线程消失。重置后会清空认领时间，使旧执行者的结果写入
     * 因认领时刻不再匹配而自然失效，不会被过期结果覆盖。</p>
     *
     * @param id 任务ID
     * @return 影响行数
     * @author Henfon
     * @date 2026-09-16
     */
    @Update("UPDATE export_task SET status = 'PENDING', started_at = NULL, claimed_at = NULL, finished_at = NULL, "
            + "error_message = NULL WHERE id = #{id} AND status = 'RUNNING'")
    int rescueStaleRunning(@Param("id") Long id);

    /**
     * 标记任务文件已过期，同时清空文件引用，避免残留可下载的失效地址。
     *
     * @param id 任务ID
     * @return 影响行数
     * @author Henfon
     * @date 2026-09-16
     */
    @Update("UPDATE export_task SET status = 'EXPIRED', object_key = NULL, file_name = NULL, file_size = NULL "
            + "WHERE id = #{id} AND status = 'SUCCESS'")
    int markExpired(@Param("id") Long id);

    /**
     * 将失败任务重新排队，供提交人重试。
     *
     * @param id 任务ID
     * @return 影响行数
     * @author Henfon
     * @date 2026-09-16
     */
    @Update("UPDATE export_task SET status = 'PENDING', started_at = NULL, claimed_at = NULL, finished_at = NULL, "
            + "error_message = NULL WHERE id = #{id} AND status = 'FAILED'")
    int requeue(@Param("id") Long id);

    /**
     * 回填任务编号。
     *
     * @param id 任务ID
     * @param taskNo 任务编号
     * @return 影响行数
     * @author Henfon
     * @date 2026-09-16
     */
    @Update("UPDATE export_task SET task_no = #{taskNo} WHERE id = #{id} AND task_no IS NULL")
    int assignTaskNo(@Param("id") Long id, @Param("taskNo") String taskNo);

    /**
     * 提交人移除记录，采用逻辑删除保留审计痕迹。
     *
     * @param id 任务ID
     * @return 影响行数
     * @author Henfon
     * @date 2026-09-16
     */
    @Update("UPDATE export_task SET is_deleted = 1 WHERE id = #{id}")
    int softDelete(@Param("id") Long id);

    /**
     * 查询长时间停留在 PENDING 的任务，用于 MQ 不可用或消息丢失时兜底。
     *
     * @param deadline 提交时间早于该时刻视为需要兜底
     * @param limit 单次处理上限
     * @return 待兜底任务
     * @author Henfon
     * @date 2026-09-16
     */
    @Select("SELECT * FROM export_task WHERE status = 'PENDING' AND created_at < #{deadline} "
            + "ORDER BY id LIMIT #{limit}")
    List<ExportTask> findStalePending(@Param("deadline") LocalDateTime deadline, @Param("limit") int limit);

    /**
     * 查询认领后长时间未完成的僵尸任务，通常由进程重启导致。
     *
     * @param deadline 认领时间早于该时刻视为僵尸任务
     * @param limit 单次处理上限
     * @return 僵尸任务
     * @author Henfon
     * @date 2026-09-16
     */
    @Select("SELECT * FROM export_task WHERE status = 'RUNNING' AND claimed_at < #{deadline} "
            + "ORDER BY id LIMIT #{limit}")
    List<ExportTask> findStaleRunning(@Param("deadline") LocalDateTime deadline, @Param("limit") int limit);

    /**
     * 查询已到期的成功任务。
     *
     * @param now 当前时间
     * @param limit 单次处理上限
     * @return 待清理任务
     * @author Henfon
     * @date 2026-09-16
     */
    @Select("SELECT * FROM export_task WHERE status = 'SUCCESS' AND expires_at IS NOT NULL AND expires_at < #{now} "
            + "ORDER BY id LIMIT #{limit}")
    List<ExportTask> findExpired(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
