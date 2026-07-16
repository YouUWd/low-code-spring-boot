-- 数据快照表
CREATE TABLE IF NOT EXISTS `sys_data_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `table_name` varchar(100) NOT NULL COMMENT '表名',
  `data_id` bigint NOT NULL COMMENT '数据ID',
  `json_data` json NOT NULL COMMENT 'JSON数据快照',
  `version` int NOT NULL DEFAULT 1 COMMENT '版本号（乐观锁）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_table_data` (`table_name`, `data_id`),
  KEY `idx_table_name` (`table_name`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据快照表';
