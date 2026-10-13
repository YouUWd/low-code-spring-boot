-- company 项目 data 引擎 H2 集成测试 · 种子数据（精简版）
--
-- 目的：验证 data-biz 读 / 写链路的**逻辑自洽性**，因此只保留刚好够用的数据形态：
--   * 每张物理表总行数控制在 2~5 行（1:1 表靠不同聚合根凑够 2 行）；
--   * json 列一律精简为 [{"word_describe":"..","word_id":N}]，只保留引擎会解析的字段；
--   * 保留两类边界值：空 JSON 数组（[]）与 NULL。
--
-- 三个聚合根：
--   info.id=100  完整聚合根：大型国企集团（9 张物理子表齐全，共 17 行）
--   info.id=101  稀疏聚合根：初创科技企业（仅包含 credit_rating + address，缺 basic_info / member 等）
--   info.id=102  极简聚合根：个体工商户（仅包含 basic_info）
--
-- 由 CompanyH2Fixture.initSeed(ds) 加载并一次性 execute。

-- ============================================================
-- info（3 行：100 / 101 / 102）
-- ============================================================
INSERT INTO info (id, business_no, version_no, subject_id, company_name, parent_id, nature, reg_status, cooperation_types, industry_tags, industry_types, logo_url, gender, business_status, created_by, updated_by, created_date, updated_date, deleted) VALUES (100, 'CR202609110137000100', 3, 0, '石嘴山市盛港煤焦化有限公司1010', 1, '[{"word_describe":"企业","word_id":478}]', '[{"word_describe":"存续","word_id":455}]', '[{"word_describe":"客户","word_id":487}]', '[{"word_describe":"兰炭","word_id":490}]', '[{"word_describe":"制造业","word_id":492}]', 'https://oss.example.com/company/logo/sgmjh.png', NULL, 10, 295, 298, '2026-09-11 01:37:22', '2026-10-10 15:27:28', 0);
INSERT INTO info (id, business_no, version_no, subject_id, company_name, parent_id, nature, reg_status, cooperation_types, industry_tags, industry_types, logo_url, gender, business_status, created_by, updated_by, created_date, updated_date, deleted) VALUES (101, 'CR202609302103000101', 1, 0, '北京极光智联科技有限公司', NULL, '[{"word_describe":"企业","word_id":478}]', '[{"word_describe":"存续","word_id":455}]', '[{"word_describe":"供应商","word_id":488}]', '[{"word_describe":"软件开发","word_id":495}]', '[{"word_describe":"信息传输、软件和信息技术服务业","word_id":496}]', 'https://oss.example.com/company/logo/jgzl.png', NULL, 1, 295, 295, '2026-09-30 21:03:35', '2026-10-10 01:40:17', 0);
INSERT INTO info (id, business_no, version_no, subject_id, company_name, parent_id, nature, reg_status, cooperation_types, industry_tags, industry_types, logo_url, gender, business_status, created_by, updated_by, created_date, updated_date, deleted) VALUES (102, 'CR202610080906000102', 1, 0, '杭州西湖区晨光文具经营部', NULL, '[{"word_describe":"个体工商户","word_id":479}]', '[{"word_describe":"存续","word_id":455}]', NULL, '[]', '[]', NULL, '男', 1, 295, 295, '2026-10-08 09:06:20', '2026-10-10 01:40:17', 0);

-- ============================================================
-- basic_info（2 行：100 / 102）· 1:1 伴生
-- ============================================================
INSERT INTO basic_info (id, business_no, version_no, subject_id, company_id, uscc, short_name, en_short_name, former_name, former_short_name, en_former_name, en_former_short_name, taxpayer_qualification, website, legal_person, economic_nature, insured_count, reg_capital, paid_in_capital, company_type, reg_date, business_term_start, business_term_end, is_long_term, id_card_front_url, id_card_back_url, license_url, business_status, created_by, updated_by, created_date, updated_date, deleted) VALUES (1, 'CBI202609110137000001', 1, 0, 100, '9153312343631123J', '盛港焦化', 'SG Coking', NULL, NULL, NULL, NULL, '["一般纳税人"]', 'https://www.sgmjh.example.com', '李雨桐', '[{"word_describe":"国有企业","word_id":498}]', 500, '5000.00', '3000.00', '["有限责任公司"]', '2014-04-28', '2014-04-28', '2024-04-28', 0, NULL, 'https://oss.example.com/idcard/back.jpg', 'https://oss.example.com/license/cert.jpg', NULL, NULL, 294, '2026-09-11 01:37:22', '2026-10-10 01:40:21', 0);
INSERT INTO basic_info (id, business_no, version_no, subject_id, company_id, uscc, short_name, en_short_name, former_name, former_short_name, en_former_name, en_former_short_name, taxpayer_qualification, website, legal_person, economic_nature, insured_count, reg_capital, paid_in_capital, company_type, reg_date, business_term_start, business_term_end, is_long_term, id_card_front_url, id_card_back_url, license_url, business_status, created_by, updated_by, created_date, updated_date, deleted) VALUES (2, 'CBI202610080906000002', 1, 0, 102, '92330106MA2H88888X', '晨光文具', NULL, NULL, NULL, NULL, NULL, '["小规模纳税人"]', NULL, '张晨光', '[{"word_describe":"个体工商户","word_id":479}]', 2, '10.00', '10.00', '[{"word_describe":"个体工商户","word_id":479}]', '2020-05-18', '2020-05-18', NULL, 1, 'https://oss.example.com/idcard/cg_front.jpg', 'https://oss.example.com/idcard/cg_back.jpg', 'https://oss.example.com/license/cg_cert.jpg', NULL, 295, 295, '2026-10-08 09:06:20', '2026-10-10 01:40:21', 0);

-- ============================================================
-- address（2 行：100 / 101）· 1:1 伴生，挂在虚拟模块 1020 下
-- ============================================================
INSERT INTO address (id, business_no, version_no, subject_id, company_id, comm_country, comm_region, comm_detail, comm_postcode, reg_country, reg_region, reg_detail, created_by, updated_by, created_date, updated_date, deleted) VALUES (1, 'CCA202609110137000001', 1, 0, 100, '中国', '宁夏回族自治区/石嘴山市/大武口区', '长胜南街工业园区88号', '753000', '中国', '宁夏回族自治区/石嘴山市/大武口区', '长胜南街工业园区88号办公楼301', NULL, 295, '2026-09-11 01:37:22', '2026-10-10 01:40:23', 0);
INSERT INTO address (id, business_no, version_no, subject_id, company_id, comm_country, comm_region, comm_detail, comm_postcode, reg_country, reg_region, reg_detail, created_by, updated_by, created_date, updated_date, deleted) VALUES (2, 'CCA202609302103000002', 1, 0, 101, '中国', '北京市/北京市/海淀区', '中关村南大街1号互联网创新中心A座8层', '100086', '中国', '北京市/北京市/海淀区', '中关村南大街1号互联网创新中心A座8层801', NULL, 295, '2026-09-30 21:03:35', '2026-10-10 01:40:23', 0);

-- ============================================================
-- credit_rating（2 行：100 / 101）· 1:1 伴生
-- ============================================================
INSERT INTO credit_rating (id, business_no, version_no, subject_id, company_id, internal_level, qcc_score, created_by, updated_by, created_date, updated_date, deleted) VALUES (1, 'CC202609110137000001', 1, 0, 100, '[{"word_describe":"AAA级","word_id":513}]', '85分', NULL, 294, '2026-09-11 01:37:22', '2026-10-10 01:40:26', 0);
INSERT INTO credit_rating (id, business_no, version_no, subject_id, company_id, internal_level, qcc_score, created_by, updated_by, created_date, updated_date, deleted) VALUES (2, 'CC202609302103000002', 1, 0, 101, '[{"word_describe":"AA级","word_id":514}]', '92分', 295, 295, '2026-09-30 21:03:36', '2026-10-10 01:40:26', 0);

-- ============================================================
-- company_contact（2 行，均属 100）· 1:N，挂在虚拟模块 1020 下
-- ============================================================
INSERT INTO company_contact (id, business_no, version_no, subject_id, company_id, contact_name, contact_type, contact_value, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (1, 'CCB202609110137000001', 1, 0, 100, NULL, '[{"word_describe":"手机","word_id":59}]', '13585855898', 1, NULL, 295, '2026-09-11 01:37:22', '2026-10-10 01:40:28', 0);
INSERT INTO company_contact (id, business_no, version_no, subject_id, company_id, contact_name, contact_type, contact_value, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (2, 'CCB202609110137000002', 1, 0, 100, NULL, '[{"word_describe":"座机","word_id":517}]', '0710-3255895', 2, NULL, 295, '2026-09-11 01:37:22', '2026-10-10 01:40:28', 0);

-- ============================================================
-- financial_account（2 行，均属 100）· 1:N，挂在虚拟模块 1007 下
-- ============================================================
INSERT INTO financial_account (id, business_no, version_no, subject_id, company_id, bank_head, bank_branch, account_name, account_no, account_status, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (1, 'CBA202609110137000001', 1, 0, 100, '[{"word_describe":"中国建设银行","word_id":130}]', '中国工商银行股份有限公司苏州工业园区支行', '石嘴山市盛港煤焦化有限公司', '62001646341059667358', '[{"word_describe":"正常","word_id":510}]', 1, NULL, 294, '2026-09-11 01:37:22', '2026-10-10 01:40:36', 0);
INSERT INTO financial_account (id, business_no, version_no, subject_id, company_id, bank_head, bank_branch, account_name, account_no, account_status, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (2, 'CBA202609110137000002', 1, 0, 100, '[{"word_describe":"中国农业银行","word_id":128}]', '中国农业银行石嘴山分行营业部', '石嘴山市盛港煤焦化有限公司', '6228480402568912345', '[{"word_describe":"冻结","word_id":511}]', 2, NULL, 294, '2026-09-11 01:37:22', '2026-10-10 01:40:36', 0);

-- ============================================================
-- digital_account（3 行，均属 100）· 1:N，挂在虚拟模块 1007 下
-- ============================================================
INSERT INTO digital_account (id, business_no, version_no, subject_id, company_id, platform, real_name, account_no, account_status, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (3, 'CDA202609110137000003', 1, 0, 100, '[{"word_describe":"支付宝","word_id":515}]', '李玉涛', '13598888888', '[{"word_describe":"正常","word_id":510}]', 1, NULL, 294, '2026-09-11 01:37:22', '2026-10-10 01:40:38', 0);
INSERT INTO digital_account (id, business_no, version_no, subject_id, company_id, platform, real_name, account_no, account_status, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (4, 'CDA202609110137000004', 1, 0, 100, '[{"word_describe":"支付宝","word_id":515}]', '百果园3', 'ZFB001', '[{"word_describe":"正常","word_id":510}]', 2, NULL, 294, '2026-09-11 01:37:22', '2026-10-10 01:40:38', 0);
INSERT INTO digital_account (id, business_no, version_no, subject_id, company_id, platform, real_name, account_no, account_status, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (5, 'CDA202609180909000005', 1, 0, 100, '[{"word_describe":"支付宝","word_id":515}]', '李玉涛', 'WX001', '[{"word_describe":"正常","word_id":510}]', 3, NULL, 294, '2026-09-18 09:09:23', '2026-10-10 01:40:38', 0);

-- ============================================================
-- member（3 行，均属 100）· 1:N
--   1001 / 1002 是部门（parent_id=0），2001 是挂在 1001 下的员工
-- ============================================================
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (1001, 'CM202609110137001001', 1, 0, 100, 'DEPT', 0, '经理办', '经理办', 'W12234', 2001, NULL, 1, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (1002, 'CM202609110137001002', 1, 0, 100, 'DEPT', 0, '财务部', '财务部', 'W12235', NULL, NULL, 2, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (2001, 'CM202609110137002001', 1, 0, 100, 'EMPLOYEE', 1001, '李玉林', '李玉林', 'EMP001', NULL, '生产车间主任', 1, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);

-- ============================================================
-- member_contact（4 行）· 孙表，挂在 member 行内
--   2 条属 1001（部门）、2 条属 2001（员工）—— 用于验证「孙表按父行正确分组」
-- ============================================================
INSERT INTO member_contact (id, business_no, version_no, subject_id, member_id, contact_type, contact_value, is_primary, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (1, 'CMC202609110137000001', 1, 0, 1001, NULL, '13585855898', 1, 1, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:33', 0);
INSERT INTO member_contact (id, business_no, version_no, subject_id, member_id, contact_type, contact_value, is_primary, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (2, 'CMC202609110137000002', 1, 0, 1001, NULL, '0710-3255895', 0, 2, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:33', 0);
INSERT INTO member_contact (id, business_no, version_no, subject_id, member_id, contact_type, contact_value, is_primary, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (3, 'CMC202609110137000003', 1, 0, 2001, NULL, '13800000011', 1, 1, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:33', 0);
INSERT INTO member_contact (id, business_no, version_no, subject_id, member_id, contact_type, contact_value, is_primary, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (4, 'CMC202609110137000004', 1, 0, 2001, NULL, '13800000012', 0, 2, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:33', 0);
