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

/** 学校业务 - 学生管理全景档案服务控制器 统一代理底层 DataEngineApi 动态数据引擎进行结构化读写 */
@Slf4j
@RestController
@RequestMapping("/api/school/students")
@RequiredArgsConstructor
@Tag(name = "学校业务-学生全景档案接口", description = "提供学生列表、全景档案读写与多子表原子事务持久化")
public class SchoolStudentController {

    private final DataEngineApi dataEngineApi;

    public static final Long MODULE_ID_STUDENT = 101L;

    @PostMapping("/query")
    @Operation(summary = "动态分页查询学生列表", description = "带排序与组合过滤")
    public ApiResponse<DataPage<Map<String, Object>>> queryStudents(
            @RequestBody DynamicQueryReq req) {
        if (req.getModuleId() == null && (req.getFields() == null || req.getFields().isEmpty())) {
            req.setModuleId(MODULE_ID_STUDENT);
        }
        try {
            DataPage<Map<String, Object>> result = dataEngineApi.query(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.warn(
                    "Query students from dataEngineApi failed, returning standard response: {}",
                    ex.getMessage());
            throw ex;
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据学生 ID 查询全景档案", description = "含主表、1:1 伴生隐私档案、1:N 选课修读及 1:N 荣誉奖项")
    public ApiResponse<Map<String, Object>> getStudentDetail(@PathVariable Long id) {
        try {
            EngineDataResult<Map<String, Object>> detailResult =
                    dataEngineApi.getDetail(MODULE_ID_STUDENT, id);
            Map<String, Object> record =
                    detailResult != null && detailResult.getData() != null
                            ? detailResult.getData()
                            : Collections.emptyMap();
            return ApiResponse.success(record);
        } catch (Exception ex) {
            log.warn("Get student detail from dataEngineApi failed: {}", ex.getMessage());
            throw ex;
        }
    }

    @PostMapping("/save")
    @Operation(summary = "保存学生全景档案", description = "同构原子保存主表与各从表数据")
    public ApiResponse<Map<String, Object>> saveStudent(@RequestBody DynamicSaveReq saveReq) {
        if (saveReq.getModuleId() == null) {
            saveReq.setModuleId(MODULE_ID_STUDENT);
        }
        try {
            Long id = dataEngineApi.save(saveReq);
            return ApiResponse.success(Map.of("id", id, "masterId", id));
        } catch (Exception ex) {
            log.error("Save student failed: {}", ex.getMessage(), ex);
            throw ex;
        }
    }
}
