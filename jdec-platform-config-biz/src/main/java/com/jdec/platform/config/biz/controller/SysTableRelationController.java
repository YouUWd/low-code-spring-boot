package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysTableRelationApi;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "全局表关联管理", description = "物理表之间客观逻辑关联关系管理")
@RestController
@RequestMapping("/api/config/table-relations")
@RequiredArgsConstructor
public class SysTableRelationController {

    private final SysTableRelationApi sysTableRelationService;

    @Operation(summary = "查询全局表关联列表", description = "获取当前项目下的所有物理表逻辑关联关系")
    @GetMapping
    public ApiResponse<List<TableRelationDTO>> listRelations() {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        List<TableRelationDTO> list = sysTableRelationService.listRelations(projectNo, subjectId);
        return ApiResponse.success(list);
    }

    @Operation(summary = "保存或更新表关联", description = "创建或编辑单条物理表逻辑关联关系")
    @PostMapping
    public ApiResponse<Long> saveRelation(@RequestBody TableRelationDTO dto) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        Long id = sysTableRelationService.saveRelation(projectNo, subjectId, dto);
        return ApiResponse.success("保存成功", id);
    }

    @Operation(summary = "删除表关联", description = "删除单条物理表逻辑关联关系")
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteRelation(@PathVariable Long id) {
        sysTableRelationService.deleteRelation(id);
        return ApiResponse.success("删除成功", null);
    }
}
