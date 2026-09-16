-- =====================================================================
-- 导出任务表
--
-- 管理端导出统一改为“提交任务 → 后台生成 Excel → 顶栏下载中心取件”，
-- 该表是任务状态与文件产物的唯一事实来源，刷新页面或重启进程都不会丢任务。
--
-- 状态流转：PENDING → RUNNING → SUCCESS / FAILED → EXPIRED
-- 任务编号 task_no 在插入后由自增主键回填，避免并发提交时编号冲突。
-- =====================================================================

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS `export_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_no` varchar(32) DEFAULT NULL COMMENT '任务编号，形如 EXP20260916-0001，插入后由主键回填',
  `export_type` varchar(32) NOT NULL COMMENT '导出类型：PRODUCT/ORDER/MEMBER/COUPON/FINANCE/PRODUCT_RANKING/LOGIN_LOG/OPERATION_LOG/STOCK_LOG',
  `export_name` varchar(64) NOT NULL COMMENT '导出名称，如商品库数据导出',
  `query_params` varchar(1000) DEFAULT NULL COMMENT '提交时的筛选条件 JSON 快照，用于追溯与重跑',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING待执行/RUNNING生成中/SUCCESS成功/FAILED失败/EXPIRED已过期',
  `file_name` varchar(160) DEFAULT NULL COMMENT '生成的文件名，含导出时间戳',
  `object_key` varchar(255) DEFAULT NULL COMMENT 'Excel 文件在 MinIO 的对象键',
  `file_size` bigint unsigned DEFAULT NULL COMMENT '文件大小，单位字节',
  `row_count` int unsigned DEFAULT NULL COMMENT '导出的数据行数，不含表头',
  `error_message` varchar(500) DEFAULT NULL COMMENT '失败原因，列表直接展示给提交人',
  `requested_by` bigint unsigned NOT NULL COMMENT '提交人管理员ID',
  `requested_by_name` varchar(64) DEFAULT NULL COMMENT '提交人名称快照，避免账号改名后历史任务无法辨认',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '任务提交时间',
  `started_at` datetime(3) DEFAULT NULL COMMENT '开始生成时间',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '生成结束时间',
  `claimed_at` datetime(3) DEFAULT NULL COMMENT '最近一次被认领执行的时间，超时未完成的任务据此判为僵尸任务',
  `expires_at` datetime(3) DEFAULT NULL COMMENT '文件过期时间，到期后清理 MinIO 对象并置为 EXPIRED',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT 0 COMMENT '提交人是否已移除记录：1已移除，0正常',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_export_task_no` (`task_no`),
  KEY `idx_export_task_owner` (`requested_by`, `is_deleted`, `id`),
  KEY `idx_export_task_status` (`status`, `claimed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导出任务表';
