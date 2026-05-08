package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.config.biz.dto.FilterRule;
import com.jdec.platform.config.biz.dto.SortRule;
import com.jdec.platform.config.biz.service.DynamicQueryService;
import com.jdec.platform.config.biz.service.SysModuleService;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
@Tag(name = "数据查询", description = "动态数据查询接口")
public class DataQueryController {

    private final SysModuleService sysModuleService;
    private final DynamicQueryService dynamicQueryService;

    /**
     * 动态查询请求对象
     */
    @Data
    @Schema(description = "动态查询请求")
    public static class QueryRequest {
        
        @Schema(description = "页码（从1开始）", example = "1", defaultValue = "1")
        private Integer page = 1;
        
        @Schema(description = "每页大小", example = "20", defaultValue = "20")
        private Integer pageSize = 20;
        
        @Schema(description = "排序规则列表（支持多字段排序）")
        private List<SortRule> sorts;
        
        @Schema(description = "过滤条件列表（多条件之间使用 AND 连接）")
        private List<FilterRule> filters;
    }

    /**
     * 根据模块编码执行动态查询
     * 
     * @param moduleCode 模块编码
     * @param request 查询请求参数
     * @return 分页查询结果
     */
    @PostMapping("/{moduleCode}")
    @Operation(summary = "执行动态查询", description = "根据模块配置动态构建SQL并执行查询，支持多种过滤操作符和多字段排序")
    @DataSource(DataSourceConstants.SCHOOL)
    public ApiResponse<PageResult<Map<String, Object>>> executeQuery(
            @PathVariable @Parameter(description = "模块编码", example = "MOD-STUDENT-LIST") String moduleCode,
            @RequestBody QueryRequest request) {
        
        log.info("执行动态查询: moduleCode={}, page={}, pageSize={}, sorts={}, filters={}", 
                moduleCode, request.getPage(), request.getPageSize(), 
                request.getSorts(), request.getFilters());
        
        // 根据模块编码查询模块配置
        SysModuleCompleteResp moduleConfig = sysModuleService.getModuleCompleteByCode(moduleCode);
        
        if (moduleConfig == null) {
            throw new BusinessException("模块不存在: " + moduleCode);
        }
        
        // 验证过滤条件字段是否合法
        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            validateFilters(request.getFilters(), moduleConfig);
        }
        
        // 验证排序字段是否合法
        if (request.getSorts() != null && !request.getSorts().isEmpty()) {
            validateSorts(request.getSorts(), moduleConfig);
        }
        
        // 执行动态查询
        PageResult<Map<String, Object>> result = dynamicQueryService.executeQuery(
                moduleConfig, 
                request.getPage(), 
                request.getPageSize(), 
                request.getSorts(), 
                request.getFilters());
        
        return ApiResponse.success(result);
    }

    /**
     * 验证过滤条件字段是否合法
     * 
     * @param filters 过滤条件
     * @param moduleConfig 模块配置
     * @throws BusinessException 如果存在非法字段
     */
    private void validateFilters(List<FilterRule> filters, SysModuleCompleteResp moduleConfig) {
        for (FilterRule filter : filters) {
            if (!isValidField(filter.getField(), moduleConfig)) {
                throw new BusinessException("非法的过滤字段: " + filter.getField());
            }
        }
    }

    /**
     * 验证排序字段是否合法
     * 
     * @param sorts 排序规则
     * @param moduleConfig 模块配置
     * @throws BusinessException 如果存在非法字段
     */
    private void validateSorts(List<SortRule> sorts, SysModuleCompleteResp moduleConfig) {
        for (SortRule sort : sorts) {
            if (!isValidField(sort.getField(), moduleConfig)) {
                throw new BusinessException("非法的排序字段: " + sort.getField());
            }
        }
    }

    /**
     * 检查字段是否为有效的字段
     * 
     * @param fieldName 字段名
     * @param moduleConfig 模块配置
     * @return 是否有效
     */
    private boolean isValidField(String fieldName, SysModuleCompleteResp moduleConfig) {
        // 检查简单字段
        if (moduleConfig.getSimpleFields() != null) {
            for (SysModuleCompleteResp.SimpleFieldInfo field : moduleConfig.getSimpleFields()) {
                if (fieldName.equals(field.getColumnName())) {
                    return true;
                }
            }
        }
        
        // 检查组合字段
        if (moduleConfig.getCombineFields() != null) {
            for (SysModuleCompleteResp.CombineFieldInfo field : moduleConfig.getCombineFields()) {
                if (fieldName.equals(field.getLogicalField()) && field.getEnabled() == 1) {
                    return true;
                }
            }
        }
        
        // 检查主键字段
        return "id".equals(fieldName);
    }
}
