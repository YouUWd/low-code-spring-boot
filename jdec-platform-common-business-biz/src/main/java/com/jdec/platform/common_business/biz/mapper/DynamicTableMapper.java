package com.jdec.platform.common_business.biz.mapper;

import com.jdec.platform.common_business.api.dto.common.ModuleField;
import com.jdec.platform.common_business.api.dto.common.ModuleInfo;
import com.jdec.platform.common_business.api.dto.common.ModuleTable;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 动态表名查询 Mapper — 对应 PHP DataSourceLogic 中通过 Eloquent 对动态 model_class 执行查询。
 *
 * <p>由于 MyBatis-Plus 不直接支持动态表名 + 动态列名，此处使用 XML Mapper 方式通过 ${} 拼接。 注意：表名和列名均来自 sys_module
 * 内部配置，非用户输入，不存在 SQL 注入风险。
 */
@Mapper
public interface DynamicTableMapper {

    /**
     * 从指定表中提取单列数据（含基础过滤条件）。
     *
     * <p>对应 PHP: $query->pluck($field)->toArray()
     *
     * @param tableName 业务表名
     * @param field 需要提取的列名
     * @param subjectId 主体ID过滤
     * @param hasApproval 是否有审批链（决定是否加 is_history 条件）
     * @return 字段值列表
     */
    List<String> pluckField(
            @Param("tableName") String tableName,
            @Param("field") String field,
            @Param("subjectId") Long subjectId,
            @Param("hasApproval") boolean hasApproval);

    /**
     * 从指定表中提取单列数据（带审批链 + 数据权限条件）。
     *
     * <p>对应 PHP: getQueryWithDataAccess -> getGeneralApprovalChainQuery ->
     * DataAccessManagerLogic::getDataAccessBuilder -> pluck($field)
     *
     * @param tableName 业务表名
     * @param field 需要提取的列名
     * @param subjectId 主体ID过滤
     * @param type 查询类型 (1=待办, 2=已办, 3=全部)
     * @param userId 当前登录用户ID
     * @param roleId 当前角色ID
     * @param rightSlug 模块权限标识 (如 "User-create")
     * @param dataAccessField 数据权限字段名 (可为空)
     * @param dataAccessValues 数据权限值列表 (可为空)
     * @return 字段值列表
     */
    List<String> pluckFieldWithApproval(
            @Param("tableName") String tableName,
            @Param("field") String field,
            @Param("subjectId") Long subjectId,
            @Param("type") String type,
            @Param("userId") Long userId,
            @Param("roleId") Long roleId,
            @Param("rightSlug") String rightSlug,
            @Param("dataAccessField") String dataAccessField,
            @Param("dataAccessValues") List<String> dataAccessValues);

    /**
     * 从指定表中提取单列数据（带审批链 + 数据权限条件）。
     *
     * <p>对应 PHP: getQueryWithDataAccess -> getGeneralApprovalChainQuery ->
     * DataAccessManagerLogic::getDataAccessBuilder -> pluck($field)
     *
     * @param tableName 业务表名
     * @param field 需要提取的列名
     * @param subjectId 主体ID过滤
     * @param type 查询类型 (1=待办, 2=已办, 3=全部)
     * @param userId 当前登录用户ID
     * @param roleId 当前角色ID
     * @param rightSlug 模块权限标识 (如 "User-create")
     * @param dataAccessField 数据权限字段名 (可为空)
     * @param dataAccessValues 数据权限值列表 (可为空)
     * @return 字段值列表
     */
    List<String> pluckFieldWithApprovalWithDelete(
            @Param("tableName") String tableName,
            @Param("field") String field,
            @Param("subjectId") Short subjectId,
            @Param("type") String type,
            @Param("userId") Long userId,
            @Param("roleId") Long roleId,
            @Param("rightSlug") String rightSlug,
            @Param("dataAccessField") String dataAccessField,
            @Param("dataAccessValues") List<String> dataAccessValues);

    /**
     * 从 depend_model 对应的表中，按 whereIn 条件查询数据。
     *
     * <p>对应 PHP: $dependModelClass::whereIn($whereField, $data)->select($selectFields)->get()
     *
     * @param tableName depend_model 对应的表名
     * @param selectFields SELECT 字段（逗号分隔的完整表达式，如 "id as value, title as name"）
     * @param whereField whereIn 的条件字段
     * @param values whereIn 的值列表
     * @param subjectId 主体ID（仅 needSubjectFilter=true 时使用）
     * @param moduleId 模块ID（仅 isWordMapping=true 时使用 parent_module_id 过滤）
     * @param needSubjectFilter 是否需要 subject_id 条件
     * @param isWordMapping 是否为 WordMappingRecord（使用 parent_module_id 过滤）
     * @return 查询结果列表
     */
    List<Map<String, Object>> selectDependModelData(
            @Param("tableName") String tableName,
            @Param("selectFields") String selectFields,
            @Param("whereField") String whereField,
            @Param("values") List<String> values,
            @Param("subjectId") Long subjectId,
            @Param("moduleId") Long moduleId,
            @Param("needSubjectFilter") boolean needSubjectFilter,
            @Param("isWordMapping") boolean isWordMapping);

    /**
     * 按 user_id 统计指定表的记录数 Count records by user_id for getUserDetail count_list
     *
     * @param tableName 表名
     * @param userId 用户ID
     * @return 记录数
     */
    int countByUserId(@Param("tableName") String tableName, @Param("userId") Long userId);

    Map<String, Object> dynamicQueryData(
            ModuleInfo moduleInfo, List<ModuleTable> moduleTables, List<ModuleField> moduleFields);
}
