// package com.jdec.platform;
//
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
// import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
// import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
// import com.fasterxml.jackson.core.type.TypeReference;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.jdec.platform.config.api.dto.request.*;
// import com.jdec.platform.config.api.dto.request.SaveModuleReq.SaveSysModuleReq;
// import com.jdec.platform.config.api.dto.response.SysModuleDetailResp;
// import com.jdec.platform.config.biz.entity.SysModule;
// import com.jdec.platform.config.biz.mapper.SysModuleMapper;
// import com.jdec.platform.shared.datasource.DataSourceConstants;
// import com.jdec.platform.shared.datasource.DataSourceContextHolder;
// import com.jdec.platform.shared.model.ApiResponse;
// import java.util.ArrayList;
// import java.util.Arrays;
// import java.util.List;
// import org.junit.jupiter.api.*;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.http.MediaType;
// import org.springframework.test.web.servlet.MockMvc;
// import org.springframework.test.web.servlet.MvcResult;
//
/// **
// * 模块管理 API 测试用例 基于 school.sql 的表结构，测试 SaveModuleReq 的各种场景
// *
// * <p>测试顺序： 1. 创建 5 个测试模块（Order 1-5） 2. 清理所有测试模块（Order 999）
// */
// @SpringBootTest
// @AutoConfigureMockMvc
// @DisplayName("模块管理 API 测试")
// @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
// class SysModuleControllerTest {
//
//    @Autowired private MockMvc mockMvc;
//
//    @Autowired private ObjectMapper objectMapper;
//
//    @Autowired private SysModuleMapper sysModuleMapper;
//
//    private static final String API_BASE = "/api/config/modules";
//    private static final String PROJECT_NO = "school";
//
//    // 收集所有测试用例创建的模块 ID
//    private static final List<Long> createdModuleIds = new ArrayList<>();
//
//    @BeforeEach
//    void setUp() {
//        // 设置数据源为 CONFIG_CENTER
//        // @DataSource 注解在测试类上不生效，需要手动设置
//        DataSourceContextHolder.set(DataSourceConstants.CONFIG_CENTER);
//    }
//
//    @AfterEach
//    void tearDown() {
//        // 清理数据源上下文
//        DataSourceContextHolder.clear();
//    }
//
//    /** 执行创建请求并收集模块 ID */
//    private Long performCreateAndCollectId(SaveModuleReq request, String testName)
//            throws Exception {
//        MvcResult result =
//                mockMvc.perform(
//                                post(API_BASE + "/complete")
//                                        .contentType(MediaType.APPLICATION_JSON)
//                                        .content(objectMapper.writeValueAsString(request)))
//                        .andDo(print())
//                        .andExpect(status().isOk())
//                        .andExpect(jsonPath("$.status").value(200))
//                        .andExpect(jsonPath("$.data.id").exists())
//                        .andReturn();
//
//        // 解析响应获取模块 ID
//        String responseBody = result.getResponse().getContentAsString();
//        ApiResponse<SysModuleDetailResp> response =
//                objectMapper.readValue(
//                        responseBody, new TypeReference<ApiResponse<SysModuleDetailResp>>() {});
//
//        Long moduleId = response.getData().getId();
//        createdModuleIds.add(moduleId);
//
//        System.out.println(testName + " - 创建成功，模块 ID: " + moduleId);
//        return moduleId;
//    }
//
//    /** 测试用例 1: 学生列表模块（LIST 类型，单表） */
//    @Test
//    @Order(1)
//    @DisplayName("创建学生列表模块 - LIST 类型，单表")
//    void testCreateStudentListModule() throws Exception {
//        SaveModuleReq request = buildStudentListModule();
//        performCreateAndCollectId(request, "测试用例 1: 学生列表模块");
//    }
//
//    /** 测试用例 2: 学生成绩列表模块（LIST 类型，多表关联） */
//    @Test
//    @Order(2)
//    @DisplayName("创建学生成绩列表模块 - LIST 类型，多表关联")
//    void testCreateScoreListModule() throws Exception {
//        SaveModuleReq request = buildScoreListModule();
//        performCreateAndCollectId(request, "测试用例 2: 学生成绩列表模块");
//    }
//
//    /** 测试用例 3: 教师管理列表模块（LIST 类型，带部门关联） */
//    @Test
//    @Order(3)
//    @DisplayName("创建教师管理列表模块 - LIST 类型，带部门关联")
//    void testCreateTeacherListModule() throws Exception {
//        SaveModuleReq request = buildTeacherListModule();
//        performCreateAndCollectId(request, "测试用例 3: 教师管理列表模块");
//    }
//
//    /** 测试用例 4: 班级管理列表模块（LIST 类型，带班主任关联） */
//    @Test
//    @Order(4)
//    @DisplayName("创建班级管理列表模块 - LIST 类型，带班主任关联")
//    void testCreateClassListModule() throws Exception {
//        SaveModuleReq request = buildClassListModule();
//        performCreateAndCollectId(request, "测试用例 4: 班级管理列表模块");
//    }
//
//    /** 测试用例 5: 学生详情模块（DETAIL 类型，带状态流转） */
//    @Test
//    @Order(5)
//    @DisplayName("创建学生详情模块 - DETAIL 类型，带状态流转")
//    void testCreateStudentDetailModule() throws Exception {
//        SaveModuleReq request = buildStudentDetailModule();
//        performCreateAndCollectId(request, "测试用例 5: 学生详情模块");
//    }
//
//    /** 测试用例 6: 学生完整信息模块（包含 1:1 关系） 主表：student 1:1 关系：student_profile（假设存在学生档案表，通过 student_id 关联）
// */
//    @Test
//    @Order(6)
//    @DisplayName("创建学生完整信息模块 - 包含 1:1 关系")
//    void testCreateStudentWith11RelationModule() throws Exception {
//        SaveModuleReq request = buildStudentWith11RelationModule();
//        performCreateAndCollectId(request, "测试用例 6: 学生完整信息模块（1:1）");
//    }
//
//    /** 测试用例 7: 班级完整信息模块（包含 1:N 关系） 主表：class N:1 关系：teacher（班主任） 1:N 关系：student（班级学生列表） */
//    @Test
//    @Order(7)
//    @DisplayName("创建班级完整信息模块 - 包含 N:1 和 1:N 关系")
//    void testCreateClassWith1NRelationModule() throws Exception {
//        SaveModuleReq request = buildClassWith1NRelationModule();
//        performCreateAndCollectId(request, "测试用例 7: 班级完整信息模块（N:1 + 1:N）");
//    }
//
//    /** 测试用例 8: 课程完整信息模块（包含 1:N 关系） 主表：course 1:N 关系：score（课程成绩列表） */
//    @Test
//    @Order(8)
//    @DisplayName("创建课程完整信息模块 - 包含 1:N 关系")
//    void testCreateCourseWith1NRelationModule() throws Exception {
//        SaveModuleReq request = buildCourseWith1NRelationModule();
//        performCreateAndCollectId(request, "测试用例 8: 课程完整信息模块（1:N）");
//    }
//
//    /** 测试用例 999: 清理所有测试数据 /** 测试用例 999: 清理所有测试数据 使用删除 API 清理前面测试用例创建的所有模块 同时测试删除接口的功能 */
//    @Test
//    @Order(999)
//    @DisplayName("清理所有测试数据 - 测试删除接口")
//    void testCleanupAllTestData() throws Exception {
//        System.out.println("\n========================================");
//        System.out.println("开始清理测试数据...");
//        System.out.println("待删除的模块数量: " + createdModuleIds.size());
//        System.out.println("========================================\n");
//
//        int successCount = 0;
//        int failCount = 0;
//
//        for (Long moduleId : createdModuleIds) {
//            try {
//                System.out.println("正在删除模块 ID: " + moduleId);
//
//                mockMvc.perform(delete(API_BASE + "/" + moduleId + "/complete"))
//                        .andDo(print())
//                        .andExpect(status().isOk())
//                        .andExpect(jsonPath("$.status").value(200))
//                        .andExpect(jsonPath("$.msg").value("删除成功"));
//
//                successCount++;
//                System.out.println("✓ 模块 ID " + moduleId + " 删除成功\n");
//
//            } catch (Exception e) {
//                failCount++;
//                System.err.println("✗ 模块 ID " + moduleId + " 删除失败: " + e.getMessage() + "\n");
//            }
//        }
//
//        System.out.println("========================================");
//        System.out.println("清理完成！");
//        System.out.println("成功: " + successCount + " 个");
//        System.out.println("失败: " + failCount + " 个");
//        System.out.println("========================================\n");
//
//        // 清空列表，避免重复删除
//        createdModuleIds.clear();
//    }
//
//    /**
//     * 批量删除测试方法 可以单独运行，从数据库查询所有测试模块，然后使用 API 删除
//     *
//     * <p>使用方法： mvn test -Dtest=SysModuleControllerTest#testDeleteAllTestModules
//     */
//    @Test
//    @DisplayName("删除所有测试模块 - 从数据库查询后删除")
//    void testDeleteAllTestModules() throws Exception {
//        List<String> testModuleCodes =
//                Arrays.asList(
//                        "MOD-STUDENT-LIST",
//                        "MOD-SCORE-LIST",
//                        "MOD-TEACHER-LIST",
//                        "MOD-CLASS-LIST",
//                        "MOD-STUDENT-DETAIL",
//                        "MOD-STUDENT-COMPLETE-11",
//                        "MOD-CLASS-COMPLETE-1N",
//                        "MOD-COURSE-COMPLETE-1N");
//
//        System.out.println("\n========================================");
//        System.out.println("开始批量删除测试模块...");
//        System.out.println("租户 ID: " + PROJECT_NO);
//        System.out.println("========================================\n");
//
//        // 第一步：一次性查询所有模块 ID
//        System.out.println("第一步：查询所有测试模块...\n");
//
//        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
//        wrapper.eq(SysModule::getProjectNo, PROJECT_NO)
//                .in(SysModule::getModuleCode, testModuleCodes);
//
//        List<SysModule> modules = sysModuleMapper.selectList(wrapper);
//
//        System.out.println("✓ 查询完成，找到 " + modules.size() + " 个模块\n");
//
//        if (modules.isEmpty()) {
//            System.out.println("⊘ 未找到任何测试模块");
//            System.out.println("========================================\n");
//            return;
//        }
//
//        // 打印找到的模块信息
//        for (SysModule module : modules) {
//            System.out.println(
//                    "  - "
//                            + module.getModuleCode()
//                            + " (ID: "
//                            + module.getId()
//                            + ") - "
//                            + module.getModuleName());
//        }
//        System.out.println();
//
//        // 第二步：清理 DataSourceContextHolder，然后调用 API 删除
//        System.out.println("第二步：删除所有模块...\n");
//        DataSourceContextHolder.clear();
//
//        int successCount = 0;
//        int failCount = 0;
//
//        for (SysModule module : modules) {
//            try {
//                Long moduleId = module.getId();
//                String moduleCode = module.getModuleCode();
//
//                System.out.println("正在删除模块: " + moduleCode + " (ID: " + moduleId + ")");
//
//                // 使用 API 删除模块
//                mockMvc.perform(delete(API_BASE + "/" + moduleId + "/complete"))
//                        .andExpect(status().isOk())
//                        .andExpect(jsonPath("$.status").value(200))
//                        .andExpect(jsonPath("$.msg").value("删除成功"));
//
//                successCount++;
//                System.out.println("✓ 删除成功\n");
//
//            } catch (Exception e) {
//                failCount++;
//                System.err.println("✗ 删除失败: " + e.getMessage() + "\n");
//            }
//        }
//
//        System.out.println("========================================");
//        System.out.println("批量删除完成！");
//        System.out.println("成功: " + successCount + " 个");
//        System.out.println("失败: " + failCount + " 个");
//        System.out.println("========================================\n");
//    }
//
//    // ==================== 构建测试数据的辅助方法 ====================
//
//    /** 构建学生列表模块请求 */
//    private SaveModuleReq buildStudentListModule() {
//        SaveModuleReq request = new SaveModuleReq();
//
//        // 模块基本信息
//        SaveSysModuleReq module = new SaveSysModuleReq();
//        module.setProjectNo(PROJECT_NO);
//        module.setModuleCode("MOD-STUDENT-LIST");
//        module.setModuleName("学生列表");
//        module.setModuleDesc("学生信息列表查询模块");
//        module.setParentId(null);
//        module.setPrimaryTable("student");
//        module.setModuleType("LIST");
//        module.setSortOrder(1);
//        module.setCreatedBy(1L);
//        module.setCreatedName("系统管理员");
//
//        // 表头配置
//        module.setTableHeader(
//                Arrays.asList(
//                        createTableHeaderColumn(
//                                "学号", "student_no", 120, 1, "text", "left", false, true),
//                        createTableHeaderColumn(
//                                "姓名", "fullName", 100, 2, "text", "none", false, true),
//                        createTableHeaderColumn(
//                                "性别", "genderText", 80, 3, "select", "none", false, false),
//                        createTableHeaderColumn(
//                                "出生日期", "birth_date", 120, 4, "date", "none", false, true),
//                        createTableHeaderColumn(
//                                "班级", "className", 120, 5, "text", "none", false, false),
//                        createTableHeaderColumn(
//                                "联系电话", "contact_phone", 130, 6, "text", "none", false, false),
//                        createTableHeaderColumn(
//                                "状态", "statusText", 80, 7, "select", "right", false, false)));
//
//        request.setModule(module);
//
//        // 关联表
//        request.setModuleTables(
//                Arrays.asList(createModuleTable("class", "班级表", "class_id", "id", "N:1", 1)));
//
//        // 简单字段
//        request.setSimpleFields(
//                Arrays.asList(
//                        createSimpleField("student", "student_no", null),
//                        createSimpleField("student", "gender", null),
//                        createSimpleField("student", "birth_date", null),
//                        createSimpleField("student", "contact_phone", null),
//                        createSimpleField("student", "status", null),
//                        createSimpleField("class", "class_name", null)));
//
//        // 组合字段
//        request.setCombineFields(
//                Arrays.asList(
//                        createCombineField(
//                                "fullName",
//                                "姓名",
//                                Arrays.asList(
//                                        createSourceMapping("student", "last_name", 1),
//                                        createSourceMapping("student", "first_name", 2)),
//                                "CONCAT(${last_name}, ${first_name})",
//                                1),
//                        createCombineField(
//                                "genderText",
//                                "性别文本",
//                                Arrays.asList(createSourceMapping("student", "gender", 1)),
//                                "CASE WHEN ${gender} = 1 THEN '男' WHEN ${gender} = 2 THEN '女' ELSE
// '未知' END",
//                                1),
//                        createCombineField(
//                                "statusText",
//                                "状态文本",
//                                Arrays.asList(createSourceMapping("student", "status", 1)),
//                                "CASE WHEN ${status} = 1 THEN '在读' WHEN ${status} = 2 THEN '休学'
// WHEN ${status} = 3 THEN '毕业' ELSE '未知' END",
//                                1),
//                        createCombineField(
//                                "className",
//                                "班级名称",
//                                Arrays.asList(createSourceMapping("class", "class_name", 1)),
//                                "${class_name}",
//                                1)));
//
//        return request;
//    }
//
//    /** 构建学生成绩列表模块请求 */
//    private SaveModuleReq buildScoreListModule() {
//        SaveModuleReq request = new SaveModuleReq();
//
//        SaveSysModuleReq module = new SaveSysModuleReq();
//        module.setProjectNo(PROJECT_NO);
//        module.setModuleCode("MOD-SCORE-LIST");
//        module.setModuleName("学生成绩列表");
//        module.setModuleDesc("学生成绩查询模块，支持按学生、课程、学期查询");
//        module.setParentId(null);
//        module.setPrimaryTable("score");
//        module.setModuleType("LIST");
//        module.setSortOrder(2);
//        module.setCreatedBy(1L);
//        module.setCreatedName("系统管理员");
//
//        module.setTableHeader(
//                Arrays.asList(
//                        createTableHeaderColumn(
//                                "学生姓名", "studentName", 100, 1, "text", "left", false, true),
//                        createTableHeaderColumn(
//                                "学号", "studentNo", 120, 2, "text", "none", false, false),
//                        createTableHeaderColumn(
//                                "课程名称", "courseName", 100, 3, "text", "none", false, false),
//                        createTableHeaderColumn(
//                                "学期", "semester", 100, 4, "select", "none", false, true),
//                        createTableHeaderColumn("分数", "score", 80, 5, "range", "none", false,
// true),
//                        createTableHeaderColumn(
//                                "等级", "grade_level", 80, 6, "select", "none", false, false),
//                        createTableHeaderColumn(
//                                "考试日期", "exam_date", 120, 7, "date", "none", false, true)));
//
//        request.setModule(module);
//
//        request.setModuleTables(
//                Arrays.asList(
//                        createModuleTable("student", "学生表", "student_id", "id", "N:1", 1),
//                        createModuleTable("course", "课程表", "course_id", "id", "N:1", 2)));
//
//        request.setSimpleFields(
//                Arrays.asList(
//                        createSimpleField("score", "semester", null),
//                        createSimpleField("score", "score", null),
//                        createSimpleField("score", "grade_level", null),
//                        createSimpleField("score", "exam_date", null),
//                        createSimpleField("student", "student_no", null),
//                        createSimpleField("course", "course_name", null)));
//
//        request.setCombineFields(
//                Arrays.asList(
//                        createCombineField(
//                                "studentName",
//                                "学生姓名",
//                                Arrays.asList(
//                                        createSourceMapping("student", "last_name", 1),
//                                        createSourceMapping("student", "first_name", 2)),
//                                "CONCAT(${last_name}, ${first_name})",
//                                1),
//                        createCombineField(
//                                "studentNo",
//                                "学号",
//                                Arrays.asList(createSourceMapping("student", "student_no", 1)),
//                                "${student_no}",
//                                1),
//                        createCombineField(
//                                "courseName",
//                                "课程名称",
//                                Arrays.asList(createSourceMapping("course", "course_name", 1)),
//                                "${course_name}",
//                                1)));
//
//        return request;
//    }
//
//    /** 构建教师管理列表模块请求 */
//    private SaveModuleReq buildTeacherListModule() {
//        SaveModuleReq request = new SaveModuleReq();
//
//        SaveSysModuleReq module = new SaveSysModuleReq();
//        module.setProjectNo(PROJECT_NO);
//        module.setModuleCode("MOD-TEACHER-LIST");
//        module.setModuleName("教师管理");
//        module.setModuleDesc("教师信息管理模块");
//        module.setParentId(null);
//        module.setPrimaryTable("teacher");
//        module.setModuleType("LIST");
//        module.setSortOrder(3);
//        module.setCreatedBy(1L);
//        module.setCreatedName("系统管理员");
//
//        module.setTableHeader(
//                Arrays.asList(
//                        createTableHeaderColumn(
//                                "教师编号", "teacher_code", 120, 1, "text", "left", false, true),
//                        createTableHeaderColumn(
//                                "姓名", "teacherName", 100, 2, "text", "none", false, true),
//                        createTableHeaderColumn(
//                                "科目", "subject", 100, 3, "select", "none", false, false),
//                        createTableHeaderColumn(
//                                "部门", "departmentName", 120, 4, "select", "none", false, false),
//                        createTableHeaderColumn(
//                                "联系方式", "contactInfo", 200, 5, "text", "none", true, false),
//                        createTableHeaderColumn(
//                                "入职日期", "hire_date", 120, 6, "date", "none", false, true)));
//
//        request.setModule(module);
//
//        request.setModuleTables(
//                Arrays.asList(
//                        createModuleTable("department", "部门表", "department_id", "id", "N:1", 1)));
//
//        request.setSimpleFields(
//                Arrays.asList(
//                        createSimpleField("teacher", "teacher_code", null),
//                        createSimpleField("teacher", "subject", null),
//                        createSimpleField("teacher", "phone", null),
//                        createSimpleField("teacher", "email", null),
//                        createSimpleField("teacher", "hire_date", null),
//                        createSimpleField("department", "department_name", null)));
//
//        request.setCombineFields(
//                Arrays.asList(
//                        createCombineField(
//                                "teacherName",
//                                "教师姓名",
//                                Arrays.asList(
//                                        createSourceMapping("teacher", "last_name", 1),
//                                        createSourceMapping("teacher", "first_name", 2)),
//                                "CONCAT(${last_name}, ${first_name})",
//                                1),
//                        createCombineField(
//                                "contactInfo",
//                                "联系方式",
//                                Arrays.asList(
//                                        createSourceMapping("teacher", "phone", 1),
//                                        createSourceMapping("teacher", "email", 2)),
//                                "CONCAT('电话: ', ${phone}, ' | 邮箱: ', ${email})",
//                                1),
//                        createCombineField(
//                                "departmentName",
//                                "部门名称",
//                                Arrays.asList(
//                                        createSourceMapping("department", "department_name", 1)),
//                                "COALESCE(${department_name}, '未分配')",
//                                1)));
//
//        return request;
//    }
//
//    /** 构建班级管理列表模块请求 */
//    private SaveModuleReq buildClassListModule() {
//        SaveModuleReq request = new SaveModuleReq();
//
//        SaveSysModuleReq module = new SaveSysModuleReq();
//        module.setProjectNo(PROJECT_NO);
//        module.setModuleCode("MOD-CLASS-LIST");
//        module.setModuleName("班级管理");
//        module.setModuleDesc("班级信息管理模块");
//        module.setParentId(null);
//        module.setPrimaryTable("class");
//        module.setModuleType("LIST");
//        module.setSortOrder(4);
//        module.setCreatedBy(1L);
//        module.setCreatedName("系统管理员");
//
//        module.setTableHeader(
//                Arrays.asList(
//                        createTableHeaderColumn(
//                                "班级编号", "class_code", 120, 1, "text", "left", false, true),
//                        createTableHeaderColumn(
//                                "班级名称", "class_name", 120, 2, "text", "none", false, true),
//                        createTableHeaderColumn(
//                                "年级", "grade_level", 100, 3, "select", "none", false, false),
//                        createTableHeaderColumn(
//                                "班主任", "headTeacherName", 100, 4, "text", "none", false, false),
//                        createTableHeaderColumn(
//                                "学生人数", "student_count", 100, 5, "range", "none", false, true),
//                        createTableHeaderColumn(
//                                "班级容量", "capacity", 100, 6, "range", "none", false, false),
//                        createTableHeaderColumn(
//                                "使用率", "utilizationRate", 100, 7, "none", "right", false, true)));
//
//        request.setModule(module);
//
//        request.setModuleTables(
//                Arrays.asList(
//                        createModuleTable(
//                                "teacher", "教师表（班主任）", "head_teacher_id", "id", "N:1", 1)));
//
//        request.setSimpleFields(
//                Arrays.asList(
//                        createSimpleField("class", "class_code", null),
//                        createSimpleField("class", "class_name", null),
//                        createSimpleField("class", "grade_level", null),
//                        createSimpleField("class", "student_count", null),
//                        createSimpleField("class", "capacity", null)));
//
//        request.setCombineFields(
//                Arrays.asList(
//                        createCombineField(
//                                "headTeacherName",
//                                "班主任姓名",
//                                Arrays.asList(
//                                        createSourceMapping("teacher", "last_name", 1),
//                                        createSourceMapping("teacher", "first_name", 2)),
//                                "CONCAT(${last_name}, ${first_name})",
//                                1),
//                        createCombineField(
//                                "utilizationRate",
//                                "班级使用率",
//                                Arrays.asList(
//                                        createSourceMapping("class", "student_count", 1),
//                                        createSourceMapping("class", "capacity", 2)),
//                                "CONCAT(ROUND(${student_count} * 100.0 / NULLIF(${capacity}, 0),
// 1), '%')",
//                                1)));
//
//        return request;
//    }
//
//    /** 构建学生详情模块请求 */
//    private SaveModuleReq buildStudentDetailModule() {
//        SaveModuleReq request = new SaveModuleReq();
//
//        SaveSysModuleReq module = new SaveSysModuleReq();
//        module.setProjectNo(PROJECT_NO);
//        module.setModuleCode("MOD-STUDENT-DETAIL");
//        module.setModuleName("学生详情");
//        module.setModuleDesc("学生详细信息查看和编辑模块，支持状态流转");
//        module.setParentId(null);
//        module.setPrimaryTable("student");
//        module.setModuleType("DETAIL");
//        module.setSortOrder(5);
//        module.setCreatedBy(1L);
//        module.setCreatedName("系统管理员");
//        module.setTableHeader(new ArrayList<>()); // DETAIL 类型无需表头
//
//        request.setModule(module);
//
//        request.setModuleTables(
//                Arrays.asList(createModuleTable("class", "班级表", "class_id", "id", "N:1", 1)));
//
//        request.setSimpleFields(
//                Arrays.asList(
//                        createSimpleField("student", "student_no", null),
//                        createSimpleField("student", "first_name", null),
//                        createSimpleField("student", "last_name", null),
//                        createSimpleField("student", "gender", null),
//                        createSimpleField("student", "birth_date", null),
//                        createSimpleField("student", "enrollment_date", null),
//                        createSimpleField("student", "status", null),
//                        createSimpleField("student", "contact_phone", null),
//                        createSimpleField("class", "class_name", null),
//                        createSimpleField("class", "grade_level", null)));
//
//        request.setCombineFields(
//                Arrays.asList(
//                        createCombineField(
//                                "fullName",
//                                "完整姓名",
//                                Arrays.asList(
//                                        createSourceMapping("student", "last_name", 1),
//                                        createSourceMapping("student", "first_name", 2)),
//                                "CONCAT(${last_name}, ${first_name})",
//                                1),
//                        createCombineField(
//                                "age",
//                                "年龄",
//                                Arrays.asList(createSourceMapping("student", "birth_date", 1)),
//                                "TIMESTAMPDIFF(YEAR, ${birth_date}, CURDATE())",
//                                1),
//                        createCombineField(
//                                "classInfo",
//                                "班级信息",
//                                Arrays.asList(
//                                        createSourceMapping("class", "grade_level", 1),
//                                        createSourceMapping("class", "class_name", 2)),
//                                "CONCAT(${grade_level}, ' - ', ${class_name})",
//                                1)));
//
//        // 状态流转配置
//        request.setModuleStatuses(
//                Arrays.asList(
//                        createModuleStatus(0L, 1L),
//                        createModuleStatus(1L, 2L),
//                        createModuleStatus(1L, 3L),
//                        createModuleStatus(2L, 1L),
//                        createModuleStatus(2L, 3L)));
//
//        return request;
//    }
//
//    // ==================== 工具方法 ====================
//
//    private TableHeaderColumn createTableHeaderColumn(
//            String name,
//            String field,
//            Integer width,
//            Integer sortOrder,
//            String searchType,
//            String fixed,
//            Boolean ellipsis,
//            Boolean sortable) {
//        TableHeaderColumn column = new TableHeaderColumn();
//        column.setName(name);
//        column.setField(field);
//        column.setWidth(width);
//        column.setSortOrder(sortOrder);
//        column.setSearchType(searchType);
//        column.setFixed(fixed);
//        column.setEllipsis(ellipsis);
//        column.setSortable(sortable);
//        return column;
//    }
//
//    private SaveModuleReq.SaveModuleTableReq createModuleTable(
//            String tableName,
//            String tableDesc,
//            String joinLeftField,
//            String joinRightField,
//            String relationType,
//            Integer sortOrder) {
//        SaveModuleReq.SaveModuleTableReq table = new SaveModuleReq.SaveModuleTableReq();
//        table.setTableName(tableName);
//        table.setTableDesc(tableDesc);
//        table.setJoinLeftField(joinLeftField);
//        table.setJoinRightField(joinRightField);
//        table.setRelationType(relationType);
//        table.setSortOrder(sortOrder);
//        return table;
//    }
//
//    private SaveModuleReq.SaveSimpleFieldReq createSimpleField(
//            String tableName, String columnName, String transformer) {
//        SaveModuleReq.SaveSimpleFieldReq field = new SaveModuleReq.SaveSimpleFieldReq();
//        field.setTableName(tableName);
//        field.setColumnName(columnName);
//        field.setTransformer(transformer);
//        return field;
//    }
//
//    private SaveModuleReq.SaveCombineFieldReq createCombineField(
//            String logicalField,
//            String displayName,
//            List<SourceMappingItem> sourceMapping,
//            String transformer,
//            Integer enabled) {
//        SaveModuleReq.SaveCombineFieldReq field = new SaveModuleReq.SaveCombineFieldReq();
//        field.setLogicalField(logicalField);
//        field.setDisplayName(displayName);
//        field.setSourceMapping(sourceMapping);
//        field.setTransformer(transformer);
//        field.setEnabled(enabled);
//        return field;
//    }
//
//    private SourceMappingItem createSourceMapping(String entity, String field, Integer sortOrder)
// {
//        SourceMappingItem item = new SourceMappingItem();
//        item.setEntity(entity);
//        item.setField(field);
//        item.setSortOrder(sortOrder);
//        return item;
//    }
//
//    private SaveModuleReq.SaveModuleStatusReq createModuleStatus(Long statusPid, Long statusId) {
//        SaveModuleReq.SaveModuleStatusReq status = new SaveModuleReq.SaveModuleStatusReq();
//        status.setStatusPid(statusPid);
//        status.setStatusId(statusId);
//        return status;
//    }
//
//    /** 构建学生完整信息模块（包含 1:1 关系） 1:1 关系：student.id = student_profile.student_id */
//    private SaveModuleReq buildStudentWith11RelationModule() {
//        SaveModuleReq request = new SaveModuleReq();
//
//        SaveSysModuleReq module = new SaveSysModuleReq();
//        module.setProjectNo(PROJECT_NO);
//        module.setModuleCode("MOD-STUDENT-COMPLETE-11");
//        module.setModuleName("学生完整信息（1:1）");
//        module.setModuleDesc("学生信息模块，包含学生档案（1:1关系）");
//        module.setParentId(null);
//        module.setPrimaryTable("student");
//        module.setModuleType("DETAIL");
//        module.setSortOrder(6);
//        module.setCreatedBy(1L);
//        module.setCreatedName("系统管理员");
//        module.setTableHeader(new ArrayList<>());
//
//        request.setModule(module);
//
//        // N:1 关系：class（班级）
//        // 1:1 关系：student_profile（学生档案）
//        request.setModuleTables(
//                Arrays.asList(
//                        createModuleTable("class", "班级表", "class_id", "id", "N:1", 1),
//                        createModuleTable(
//                                "student_profile", "学生档案表", "id", "student_id", "1:1", 2)));
//
//        // 简单字段
//        request.setSimpleFields(
//                Arrays.asList(
//                        // 学生基本信息
//                        createSimpleField("student", "student_no", null),
//                        createSimpleField("student", "first_name", null),
//                        createSimpleField("student", "last_name", null),
//                        createSimpleField("student", "gender", null),
//                        createSimpleField("student", "birth_date", null),
//                        createSimpleField("student", "contact_phone", null),
//                        createSimpleField("student", "enrollment_date", null),
//                        createSimpleField("student", "status", null),
//                        // 班级信息（N:1）
//                        createSimpleField("class", "class_name", null),
//                        createSimpleField("class", "grade_level", null),
//                        // 学生档案信息（1:1）
//                        createSimpleField("student_profile", "id_card_no", null),
//                        createSimpleField("student_profile", "address", null),
//                        createSimpleField("student_profile", "parent_name", null),
//                        createSimpleField("student_profile", "parent_phone", null),
//                        createSimpleField("student_profile", "emergency_contact", null),
//                        createSimpleField("student_profile", "emergency_phone", null),
//                        createSimpleField("student_profile", "blood_type", null),
//                        createSimpleField("student_profile", "health_status", null),
//                        createSimpleField("student_profile", "allergies", null),
//                        createSimpleField("student_profile", "hobbies", null),
//                        createSimpleField("student_profile", "special_skills", null)));
//
//        // 组合字段
//        request.setCombineFields(
//                Arrays.asList(
//                        createCombineField(
//                                "fullName",
//                                "完整姓名",
//                                Arrays.asList(
//                                        createSourceMapping("student", "last_name", 1),
//                                        createSourceMapping("student", "first_name", 2)),
//                                "CONCAT(${last_name}, ${first_name})",
//                                1),
//                        createCombineField(
//                                "genderText",
//                                "性别文本",
//                                Arrays.asList(createSourceMapping("student", "gender", 1)),
//                                "CASE WHEN ${gender} = 1 THEN '男' WHEN ${gender} = 2 THEN '女' ELSE
// '未知' END",
//                                1),
//                        createCombineField(
//                                "statusText",
//                                "状态文本",
//                                Arrays.asList(createSourceMapping("student", "status", 1)),
//                                "CASE WHEN ${status} = 1 THEN '在读' WHEN ${status} = 2 THEN '休学'
// WHEN ${status} = 3 THEN '毕业' ELSE '未知' END",
//                                1),
//                        createCombineField(
//                                "classInfo",
//                                "班级信息",
//                                Arrays.asList(
//                                        createSourceMapping("class", "grade_level", 1),
//                                        createSourceMapping("class", "class_name", 2)),
//                                "CONCAT(${grade_level}, ' - ', ${class_name})",
//                                1),
//                        createCombineField(
//                                "parentContact",
//                                "家长联系方式",
//                                Arrays.asList(
//                                        createSourceMapping("student_profile", "parent_name", 1),
//                                        createSourceMapping("student_profile", "parent_phone",
// 2)),
//                                "CONCAT(${parent_name}, ' (', ${parent_phone}, ')')",
//                                1),
//                        createCombineField(
//                                "emergencyContact",
//                                "紧急联系方式",
//                                Arrays.asList(
//                                        createSourceMapping(
//                                                "student_profile", "emergency_contact", 1),
//                                        createSourceMapping(
//                                                "student_profile", "emergency_phone", 2)),
//                                "CONCAT(${emergency_contact}, ' (', ${emergency_phone}, ')')",
//                                1)));
//
//        return request;
//    }
//
//    /** 构建班级完整信息模块（包含 N:1 和 1:N 关系） N:1 关系：class → teacher（班主任） 1:N 关系：class → student（班级学生列表） */
//    private SaveModuleReq buildClassWith1NRelationModule() {
//        SaveModuleReq request = new SaveModuleReq();
//
//        SaveSysModuleReq module = new SaveSysModuleReq();
//        module.setProjectNo(PROJECT_NO);
//        module.setModuleCode("MOD-CLASS-COMPLETE-1N");
//        module.setModuleName("班级完整信息（1:N）");
//        module.setModuleDesc("班级信息模块，包含班主任（N:1）和学生列表（1:N）");
//        module.setParentId(null);
//        module.setPrimaryTable("class");
//        module.setModuleType("DETAIL");
//        module.setSortOrder(7);
//        module.setCreatedBy(1L);
//        module.setCreatedName("系统管理员");
//        module.setTableHeader(new ArrayList<>());
//
//        request.setModule(module);
//
//        // N:1 关系：teacher（班主任）
//        // 1:N 关系：student（班级学生列表）
//        request.setModuleTables(
//                Arrays.asList(
//                        createModuleTable("teacher", "教师表（班主任）", "head_teacher_id", "id", "N:1",
// 1),
//                        createModuleTable("student", "学生表（班级学生）", "id", "class_id", "1:N", 2)));
//
//        // 简单字段
//        request.setSimpleFields(
//                Arrays.asList(
//                        // 班级字段
//                        createSimpleField("class", "class_code", null),
//                        createSimpleField("class", "class_name", null),
//                        createSimpleField("class", "grade_level", null),
//                        createSimpleField("class", "student_count", null),
//                        createSimpleField("class", "capacity", null),
//                        // 班主任字段（N:1）
//                        createSimpleField("teacher", "teacher_code", null),
//                        createSimpleField("teacher", "first_name", null),
//                        createSimpleField("teacher", "last_name", null),
//                        // 学生字段（1:N）
//                        createSimpleField("student", "student_no", null),
//                        createSimpleField("student", "first_name", null),
//                        createSimpleField("student", "last_name", null),
//                        createSimpleField("student", "gender", null)));
//
//        // 组合字段
//        request.setCombineFields(
//                Arrays.asList(
//                        createCombineField(
//                                "headTeacherName",
//                                "班主任姓名",
//                                Arrays.asList(
//                                        createSourceMapping("teacher", "last_name", 1),
//                                        createSourceMapping("teacher", "first_name", 2)),
//                                "CONCAT(${last_name}, ${first_name})",
//                                1),
//                        createCombineField(
//                                "studentFullName",
//                                "学生姓名",
//                                Arrays.asList(
//                                        createSourceMapping("student", "last_name", 1),
//                                        createSourceMapping("student", "first_name", 2)),
//                                "CONCAT(${last_name}, ${first_name})",
//                                1)));
//
//        return request;
//    }
//
//    /** 构建课程完整信息模块（包含 1:N 关系） 1:N 关系：course → score（课程成绩列表） */
//    private SaveModuleReq buildCourseWith1NRelationModule() {
//        SaveModuleReq request = new SaveModuleReq();
//
//        SaveSysModuleReq module = new SaveSysModuleReq();
//        module.setProjectNo(PROJECT_NO);
//        module.setModuleCode("MOD-COURSE-COMPLETE-1N");
//        module.setModuleName("课程完整信息（1:N）");
//        module.setModuleDesc("课程信息模块，包含课程成绩列表（1:N关系）");
//        module.setParentId(null);
//        module.setPrimaryTable("course");
//        module.setModuleType("DETAIL");
//        module.setSortOrder(8);
//        module.setCreatedBy(1L);
//        module.setCreatedName("系统管理员");
//        module.setTableHeader(new ArrayList<>());
//
//        request.setModule(module);
//
//        // 1:N 关系：score（课程成绩列表）
//        request.setModuleTables(
//                Arrays.asList(
//                        createModuleTable("score", "成绩表（课程成绩）", "id", "course_id", "1:N", 1)));
//
//        // 简单字段
//        request.setSimpleFields(
//                Arrays.asList(
//                        // 课程字段
//                        createSimpleField("course", "course_code", null),
//                        createSimpleField("course", "course_name", null),
//                        createSimpleField("course", "credits", null),
//                        createSimpleField("course", "course_type", null),
//                        // 成绩字段（1:N）
//                        createSimpleField("score", "semester", null),
//                        createSimpleField("score", "score", null),
//                        createSimpleField("score", "grade_level", null),
//                        createSimpleField("score", "exam_date", null)));
//
//        // 组合字段
//        request.setCombineFields(
//                Arrays.asList(
//                        createCombineField(
//                                "courseInfo",
//                                "课程信息",
//                                Arrays.asList(
//                                        createSourceMapping("course", "course_code", 1),
//                                        createSourceMapping("course", "course_name", 2)),
//                                "CONCAT(${course_code}, ' - ', ${course_name})",
//                                1)));
//
//        return request;
//    }
// }
