-- company 项目 data 引擎 H2 集成测试 · 真实表结构
--
-- 来源：company 库 info 聚合根（模块 1001~1010、1020）对应的 9 张物理表真实 DDL
--       （列名 / 类型 / 可空性 / 默认值 / 唯一键原样保留）。
-- 范围说明：独立根模块 1021 场地产线管理（venue_line / venue / line）与 1001 聚合根无关，已剔除。
-- 由 gen_fixture.py 从真库 SHOW CREATE TABLE 生成，逐条做 H2 适配（只改语法，不改引擎行为）：
--   1. 列级 COLLATE / CHARACTER SET 删除（H2 语法不支持；表级 CHARSET= / COLLATE= 保留）；
--   2. 表级 AUTO_INCREMENT=<n> 删除（H2 会把它解析成一个名为 AUTO_INCREMENT 的列）；
--   3. 约束 / 索引名加表名前缀：H2 的约束名是 schema 级全局唯一，MySQL 是表级，
--      而 uk_company_id 在 basic_info / address / credit_rating 上重复出现；
--   4. 各 CREATE TABLE 之间补 ";"：H2 支持多语句批处理，但每条语句必须以分号结尾；
--   5. json 列改为 varchar(4000)：H2 的 JSON 类型与 MySQL 语义不同（读回时额外包裹引号、且没有
--      JSON_CONTAINS），而 MySQL 的 json 列读出来就是原始 JSON 文本。用 varchar 承载 JSON 文本，
--      恰好让引擎的 parseIfJson / MAPPER.writeValueAsString 走与生产完全一致的路径；
--   6. bigint unsigned 降级为 bigint，避免 jOOQ 的 MySQL 方言把它映射成 ULong 而干扰主键取值断言
--      （对引擎行为无影响，主键恒为正）。
--
-- 由 CompanyH2Fixture.initSchema(ds) 加载并一次性 execute（H2 支持多语句）。

CREATE TABLE `info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '业务编号',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `company_name` varchar(150) NOT NULL COMMENT '外部对象全称',
  `parent_id` bigint DEFAULT NULL COMMENT '外部对象父级全称对应ID（自关联母集团，空为顶级）',
  `nature` varchar(4000) DEFAULT NULL COMMENT '对象性质（企业/个体工商户/社会团体/政府机关等）',
  `reg_status` varchar(4000) DEFAULT NULL COMMENT '登记状态（存续/在业/注销/吊销）',
  `cooperation_types` varchar(4000) DEFAULT NULL COMMENT '合作类型，JSON数组，如: ["客户", "供应商"]',
  `industry_tags` varchar(4000) DEFAULT NULL COMMENT '行业标签，JSON数组，如: ["兰炭", "焦化"]',
  `industry_types` varchar(4000) DEFAULT NULL COMMENT '行业类型，JSON数组，如: ["纺织业", "食品制造业"]',
  `logo_url` varchar(500) DEFAULT NULL COMMENT '企业LOGO图片地址',
  `gender` varchar(255) DEFAULT NULL COMMENT '性别，对象性质为个人的时候填写',
  `business_status` int DEFAULT NULL COMMENT '业务状态',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `info_idx_company_name` (`company_name`),
  KEY `info_idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部对象主档表（顶部公共头核心信息）';

CREATE TABLE `basic_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '数据编码',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `company_id` bigint NOT NULL COMMENT '所属公司ID（关联 info.id）',
  `uscc` varchar(32) DEFAULT NULL COMMENT '统一社会信用代码',
  `short_name` varchar(100) DEFAULT NULL COMMENT '外部对象简称',
  `en_short_name` varchar(100) DEFAULT NULL COMMENT '外部对象英文简称',
  `former_name` varchar(150) DEFAULT NULL COMMENT '外部对象曾用名全称',
  `former_short_name` varchar(100) DEFAULT NULL COMMENT '外部对象曾用名简称',
  `en_former_name` varchar(150) DEFAULT NULL COMMENT '外部对象英文曾用名全称',
  `en_former_short_name` varchar(100) DEFAULT NULL COMMENT '外部对象英文曾用名简称',
  `taxpayer_qualification` varchar(4000) DEFAULT NULL COMMENT '纳税人资质（如: 一般纳税人/小规模纳税人）',
  `website` varchar(255) DEFAULT NULL COMMENT '公司网址',
  `legal_person` varchar(64) DEFAULT NULL COMMENT '法定代表人/负责人',
  `economic_nature` varchar(4000) DEFAULT NULL COMMENT '经济性质（如: 国有企业/民营企业）',
  `insured_count` int DEFAULT '0' COMMENT '参保人数',
  `reg_capital` decimal(15,2) DEFAULT '0.00' COMMENT '注册资本（万元）',
  `paid_in_capital` decimal(15,2) DEFAULT '0.00' COMMENT '实缴资本（万元）',
  `company_type` varchar(4000) DEFAULT NULL COMMENT '公司类型（如: 有限责任公司）',
  `reg_date` date DEFAULT NULL COMMENT '注册登记日期',
  `business_term_start` date DEFAULT NULL COMMENT '营业期限开始日期',
  `business_term_end` date DEFAULT NULL COMMENT '营业期限截止日期',
  `is_long_term` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '是否长期（1:是, 0:否）',
  `id_card_front_url` varchar(500) DEFAULT NULL COMMENT '身份证照片-头像面',
  `id_card_back_url` varchar(500) DEFAULT NULL COMMENT '身份证照片-国徽面',
  `license_url` varchar(500) DEFAULT NULL COMMENT '主体登记证书（营业执照）图片路径',
  `business_status` int DEFAULT NULL COMMENT '数据状态',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `basic_info_uk_company_id` (`company_id`),
  KEY `basic_info_idx_uscc` (`uscc`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部对象工商与法定资质表（Tab 1 基本信息）';

CREATE TABLE `member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '人员业务唯一标识号',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '单据快照版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `company_id` bigint DEFAULT NULL COMMENT '所属公司ID（关联 info.id）',
  `type` varchar(16) DEFAULT NULL COMMENT '节点类型（DEPT:部门, EMPLOYEE:员工）',
  `parent_id` bigint DEFAULT NULL COMMENT '父节点ID（员工指向部门，子部门指向上级部门，顶级部门为NULL）',
  `member_name` varchar(100) DEFAULT NULL COMMENT '成员名称',
  `name` varchar(100) DEFAULT NULL COMMENT '部门名称 或 员工姓名',
  `code` varchar(64) DEFAULT NULL COMMENT '外部部门号（如: W12234）或工号',
  `leader_id` bigint DEFAULT NULL COMMENT '部门负责人ID（type=DEPT时有效，关联本表员工ID）',
  `position` varchar(100) DEFAULT NULL COMMENT '职务/岗位（type=EMPLOYEE时有效）',
  `sort_order` int DEFAULT '0' COMMENT '展示排序序号',
  `status` varchar(4000) DEFAULT NULL COMMENT '状态（1:在职/启用, 0:离职/停用）',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `member_uk_member_biz_ver` (`business_no`,`version_no`,`deleted`),
  KEY `member_idx_company_parent` (`company_id`,`parent_id`),
  KEY `member_idx_company_type` (`company_id`,`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部对象组织与人员树形表（Tab 2 人员构成）';

CREATE TABLE `member_contact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '人员联系方式业务编号',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '快照版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `member_id` bigint DEFAULT NULL COMMENT '所属员工ID（关联 member.id，且必须为 type=EMPLOYEE）',
  `contact_type` varchar(4000) DEFAULT NULL COMMENT '联系方式类型（手机/座机/工作邮箱等）',
  `contact_value` varchar(100) DEFAULT NULL COMMENT '具体电话号码或邮箱地址',
  `is_primary` tinyint unsigned DEFAULT '0' COMMENT '是否为员工本人的默认联系方式（1:是, 0:否）',
  `sort_order` int DEFAULT '0' COMMENT '排序序号（用于 Tab 3 表格序号 1, 2...）',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `member_contact_idx_member_id` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人员联系方式表（兼具个人通讯录与公司对外联络窗口职责）';

CREATE TABLE `address` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '业务单据编号',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '快照版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `company_id` bigint NOT NULL COMMENT '所属公司ID（关联 info.id）',
  `comm_country` varchar(64) DEFAULT '中国' COMMENT '通讯地址-国家',
  `comm_region` varchar(128) DEFAULT NULL COMMENT '通讯地址-省/市/区',
  `comm_detail` varchar(255) DEFAULT NULL COMMENT '通讯地址-详细地址',
  `comm_postcode` varchar(16) DEFAULT NULL COMMENT '通讯地址-邮政编码',
  `reg_country` varchar(64) DEFAULT '中国' COMMENT '注册地址-国家',
  `reg_region` varchar(128) DEFAULT NULL COMMENT '注册地址-省/市/区',
  `reg_detail` varchar(255) DEFAULT NULL COMMENT '注册地址-详细地址',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `address_uk_company_id` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部对象地址信息表（Tab 3 地址部分）';

CREATE TABLE `company_contact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '业务编号',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '快照版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `company_id` bigint NOT NULL COMMENT '所属公司ID（关联 info.id）',
  `contact_name` varchar(100) DEFAULT NULL COMMENT '公司联系人',
  `contact_type` varchar(4000) DEFAULT NULL COMMENT '联系方式类型（电话/座机/邮箱/传真等）',
  `contact_value` varchar(100) DEFAULT NULL COMMENT '具体联系号码或邮箱地址',
  `sort_order` int DEFAULT '0' COMMENT '排序序号（用于前端表格行序号）',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `company_contact_uk_contact_biz_ver` (`business_no`,`version_no`,`deleted`),
  KEY `company_contact_idx_company_id` (`company_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公司联系方式表（对外联络窗口与公开渠道明细）';

CREATE TABLE `financial_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '业务单号/统一业务编号',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `company_id` bigint NOT NULL COMMENT '所属公司ID（关联 info.id）',
  `bank_head` varchar(4000) DEFAULT NULL COMMENT '行别（如: 中国建设银行）',
  `bank_branch` varchar(200) DEFAULT NULL COMMENT '开户行（支行全称）',
  `account_name` varchar(150) DEFAULT NULL COMMENT '账户名（非政府机关时前端置灰只读，并默认带出外部对象全称）',
  `account_no` varchar(100) NOT NULL COMMENT '账号/银行卡号',
  `account_status` varchar(4000) DEFAULT NULL COMMENT '账号状态（NORMAL:正常, FROZEN:冻结, CANCELLED:注销）',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '前端表格排序序号',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `financial_account_idx_company` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部对象银行卡结算账户表（Tab 4-1）';

CREATE TABLE `digital_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '业务单号/统一业务编号',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `company_id` bigint NOT NULL COMMENT '所属公司ID（关联 info.id）',
  `platform` varchar(4000) DEFAULT NULL COMMENT '平台（如: 支付宝、微信）',
  `real_name` varchar(64) DEFAULT NULL COMMENT '真实姓名/实名人',
  `account_no` varchar(100) NOT NULL COMMENT '电子账号',
  `account_status` varchar(4000) DEFAULT NULL COMMENT '账号状态（NORMAL:正常, FROZEN:冻结, CANCELLED:注销）',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '前端表格排序序号',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `digital_account_idx_company` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部对象电子结算账户表（Tab 4-2）';

CREATE TABLE `credit_rating` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '评级业务唯一标识号',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '单据快照版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `company_id` bigint NOT NULL COMMENT '所属公司ID（关联 info.id）',
  `internal_level` varchar(4000) DEFAULT NULL COMMENT '内部信用等级',
  `qcc_score` varchar(32) DEFAULT NULL COMMENT '企查查评分',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `credit_rating_uk_company_id` (`company_id`),
  UNIQUE KEY `credit_rating_uk_credit_biz_ver` (`business_no`,`version_no`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部对象信用等级表（Tab 5 信用等级）';
