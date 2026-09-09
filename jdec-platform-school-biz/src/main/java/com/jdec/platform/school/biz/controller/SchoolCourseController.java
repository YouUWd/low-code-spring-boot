package com.jdec.platform.school.biz.controller;

import com.jdec.platform.data.api.DataEngineApi;
import com.jdec.platform.data.api.dto.request.DynamicQueryReq;
import com.jdec.platform.data.api.dto.request.DynamicSaveReq;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.EngineDataResult;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 学校业务 - 课程排课管理全景档案服务控制器 统一代理底层 DataEngineApi 动态数据引擎进行结构化读写 */
@Slf4j
@RestController
@RequestMapping("/api/school/courses")
@RequiredArgsConstructor
@Tag(name = "学校业务-课程排课中心接口", description = "提供课程列表、多场景排课全景档案与成绩录入原子事务持久化")
public class SchoolCourseController {

    private final DataEngineApi dataEngineApi;

    public static final Long MODULE_ID_COURSE = 102L;

    @PostMapping("/query")
    @Operation(summary = "动态分页查询课程列表", description = "带排序与组合过滤")
    public ApiResponse<DataPage<Map<String, Object>>> queryCourses(
            @RequestBody DynamicQueryReq req) {
        if (req.getModuleId() == null && (req.getFields() == null || req.getFields().isEmpty())) {
            req.setModuleId(MODULE_ID_COURSE);
        }
        try {
            DataPage<Map<String, Object>> result = dataEngineApi.query(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.warn("Query courses from dataEngineApi failed: {}", ex.getMessage());
            throw ex;
        }
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "根据课程 ID 查询多场景排课全景档案",
            description = "含主表、1:1 教学大纲、1:N 排课日程、1:N 选课花名册及 N:1 教师团队")
    public ApiResponse<Map<String, Object>> getCourseDetail(@PathVariable Long id) {
        try {
            EngineDataResult<Map<String, Object>> detailResult =
                    dataEngineApi.getDetail(MODULE_ID_COURSE, id);
            Map<String, Object> record =
                    detailResult != null && detailResult.getData() != null
                            ? detailResult.getData()
                            : Collections.emptyMap();
            return ApiResponse.success(record);
        } catch (Exception ex) {
            log.warn("Get course detail from dataEngineApi failed: {}", ex.getMessage());
            throw ex;
        }
    }

    @PostMapping("/save")
    @Operation(summary = "保存课程全景档案", description = "同构原子保存主表与多排课子表数据")
    public ApiResponse<Map<String, Object>> saveCourse(@RequestBody DynamicSaveReq saveReq) {
        if (saveReq.getModuleId() == null) {
            saveReq.setModuleId(MODULE_ID_COURSE);
        }
        try {
            Long id = dataEngineApi.save(saveReq);
            return ApiResponse.success(Map.of("id", id, "masterId", id));
        } catch (Exception ex) {
            log.error("Save course failed: {}", ex.getMessage(), ex);
            throw ex;
        }
    }
}
