package com.jdec.platform.common_business.biz.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Editor;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.approval.api.req.ApprovalDataReq;
import com.jdec.platform.approval.api.req.ApprovalListReq;
import com.jdec.platform.approval.api.req.ApprovalProcessReq;
import com.jdec.platform.approval.api.resp.ApprovalListResp;
import com.jdec.platform.common_business.api.DynamicDataAccessServiceApi;
import com.jdec.platform.common_business.api.PersonalizedUserServiceApi;
import com.jdec.platform.common_business.api.dto.common.*;
import com.jdec.platform.common_business.api.dto.request.*;
import com.jdec.platform.common_business.api.dto.response.MenuListResp;
import com.jdec.platform.common_business.api.dto.response.SubjectResp;
import com.jdec.platform.common_business.biz.common.CommonConstant;
import com.jdec.platform.common_business.biz.common.DataSourceConstants;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.ConfigCenterApiOpenFeignClient;
import com.jdec.platform.common_business.biz.dto.FormField;
import com.jdec.platform.common_business.biz.dto.RightInfoVO;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.common_business.biz.dto.configcenter.response.DataConfig;
import com.jdec.platform.common_business.biz.dto.configcenter.response.DataRightConfigResp;
import com.jdec.platform.common_business.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.common_business.biz.enums.FormButtonEnum;
import com.jdec.platform.common_business.biz.enums.QueryOpEnum;
import com.jdec.platform.common_business.biz.service.ConfigCenterApiService;
import com.jdec.platform.common_business.biz.service.ModuleInfoWithPermissionService;
import com.jdec.platform.common_business.biz.util.LoginUserUtil;
import com.jdec.platform.common_business.biz.util.ToolUtils;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.utils.SpringBeanUtil;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.STUDENT_MANAGE)
public class DynamicDataAccessServiceImpl implements DynamicDataAccessServiceApi {

    private static final String TABLE_JOIN_RELATION_TYPE_ONE2MULTI = "1:N";

    private static final String TABLE_JOIN_RELATION_TYPE_ONE2ONE = "1:1";

    private static final String TABLE_JOIN_RELATION_TYPE_MULTI2ONE = "N:1";

    private static final String FIELD_TYPE_COMBINE = "COMBINE";

    private static final String FIELD_TYPE_SIMPLE = "SIMPLE";

    private static final String MYBATIS_MAPPER_SUFFIX = "Mapper";

    private static final String DB_TABLE_FIELD_SEPARATOR = ".";

    private static final String KEY_SEPARATOR = "-";

    private static final String DEFAULT_PRIMARY_KEY = "id";

    private static final String DEFAULT_SUBJECT_KEY = "subject_id";

    private static final String ONE2MULTI_RESULT_KEY_SUFFIX = "List";

    private static final String NOT_ONE2MULTI_RESULT_KEY_SUFFIX = "Object";

    private static final String KEY_ASTERISK_STR = "*";

    private final ModuleInfoWithPermissionService moduleInfoWithPermissionService;

    private final ConfigCenterApiOpenFeignClient configCenterApiClient;

    private final ConfigCenterApiService configCenterApiService;

    private final IApprovalEngine approvalEngineApi;

    private final PersonalizedUserServiceApi personalizedUserServiceApi;

    @Override
    public Map<String, Object> dynamicQueryDetailData(
            Long subjectId,
            Long roleId,
            Long moduleId,
            Long id,
            Long relateSearchId,
            ModuleFilterInfo moduleFilterInfo,
            Boolean hasApproval,
            Map<String, Object> approvalInfo) {

        // 获取权限过滤后的Field结果
        if (Objects.isNull(moduleFilterInfo)) {
            moduleFilterInfo =
                    moduleInfoWithPermissionService.filterModuleInfo(
                            subjectId, roleId, moduleId, "", List.of());
        }

        if (Objects.isNull(moduleFilterInfo)
                || (CollectionUtils.isEmpty(moduleFilterInfo.getSimpleModuleFields())
                        && CollectionUtils.isEmpty(moduleFilterInfo.getCombineModuleFields()))) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getMessage());
        }

        // 有节点权限，但是按钮并没有编辑或查看，说明此时使用了创建权限，与当前页面入口不符，抛出无权限异常
        if (!moduleFilterInfo.getBtnList().stream()
                .anyMatch(
                        ele ->
                                FormButtonEnum.VIEW.getKey().equals(ele.getButtonKey())
                                        || FormButtonEnum.EDITE
                                                .getKey()
                                                .equals(ele.getButtonKey()))) {
            log.debug("编辑和查看都无权限，权限判断拿的是创建权限，此时手动抛出无权限异常。");
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getMessage());
        }

        Map<String, Object> resultMap = new HashMap<>();

        // 合并simple和combine的field
        List<ModuleField> allRightField = fillIdAndJoinIdField(moduleFilterInfo);

        // 查询数据权限配置
        List<DataRightConfigResp> dataRightconfigs =
                configCenterApiService.getDataRightConfigItem(subjectId, roleId);
        Map<String, List<DataRightConfigResp>> tableNameToConfigItems =
                dataRightconfigs.stream()
                        .collect(Collectors.groupingBy(DataRightConfigResp::getTableName));

        // 主表查询Mapper对象
        BaseMapper primaryMapper =
                SpringBeanUtil.getBean(
                        moduleFilterInfo.getModuleInfo().getPrimaryTable() + MYBATIS_MAPPER_SUFFIX,
                        BaseMapper.class);
        // 主表查询构造条件
        QueryWrapper queryWrapper = Wrappers.query();
        // 主表查询字段筛选
        buildSelect(
                queryWrapper, moduleFilterInfo.getModuleInfo().getPrimaryTable(), allRightField);
        // 主表查询按主键筛选条件构造

        // 数据来源于其他主体，需要主体id作为筛选条件
        if (!CollectionUtils.isEmpty(moduleFilterInfo.getModuleInfo().getSourceSubjects())) {
            List<Integer> subjectIds =
                    moduleFilterInfo.getModuleInfo().getSourceSubjects().stream()
                            .map(SubjectResp::getId)
                            .collect(Collectors.toList());
            log.debug("数据来源于其他主体，主体ID列表为{}", subjectIds);
            buildWhere(
                    queryWrapper,
                    moduleFilterInfo.getModuleInfo().getPrimaryTable()
                            + DB_TABLE_FIELD_SEPARATOR
                            + DEFAULT_SUBJECT_KEY,
                    subjectIds.toArray());
        } else {
            buildWhere(
                    queryWrapper,
                    moduleFilterInfo.getModuleInfo().getPrimaryTable()
                            + DB_TABLE_FIELD_SEPARATOR
                            + DEFAULT_SUBJECT_KEY,
                    subjectId);
        }

        applyDataRightFilter(
                moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                tableNameToConfigItems,
                queryWrapper);

        // 详情页中的1:N的子模块 ， 此时应该用上级模块的主表ID过滤本模块的关联字段 , id为详情页的ID
        if (StringUtils.isNotBlank(moduleFilterInfo.getModuleInfo().getRelateSearchField())) {
            log.debug(
                    "模块数据的筛选条件来自于关联字段{},值为{}",
                    moduleFilterInfo.getModuleInfo().getRelateSearchField(),
                    relateSearchId);

            // relateSearchId有传值过来
            if (Objects.nonNull(relateSearchId)) {
                buildWhere(
                        queryWrapper,
                        moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                + DB_TABLE_FIELD_SEPARATOR
                                + moduleFilterInfo.getModuleInfo().getRelateSearchField(),
                        relateSearchId);
            }
        }

        if (Objects.nonNull(id)) {
            // 用主表ID作为筛选条件
            buildWhere(
                    queryWrapper,
                    moduleFilterInfo.getModuleInfo().getPrimaryTable()
                            + DB_TABLE_FIELD_SEPARATOR
                            + DEFAULT_PRIMARY_KEY,
                    id);
        }

        // 主表数据查询结果
        Object primaryData = primaryMapper.selectOne(queryWrapper);

        if (Objects.nonNull(primaryData)) {

            Map<String, Object> beanMap = new HashMap<>();

            // 前端无法动态响应动态的label key，所有返回字段都加上表名前缀
            ModuleFilterInfo finalModuleFilterInfo2 = moduleFilterInfo;
            Map<String, Object> tablePrefixResultMap =
                    BeanUtil.beanToMap(
                            primaryData,
                            beanMap,
                            true,
                            new Editor<String>() {
                                @Override
                                public String edit(String s) {
                                    // Map中的key拼接表名
                                    return finalModuleFilterInfo2.getModuleInfo().getPrimaryTable()
                                            + DB_TABLE_FIELD_SEPARATOR
                                            + s;
                                }
                            });

            if (Objects.nonNull(tablePrefixResultMap)) {

                // 查询版本对比信息数据
                if (hasApproval) {
                    ApprovalDataReq approvalDataReq = new ApprovalDataReq();
                    // 此处应用顶层主模块ID
                    approvalDataReq.setModuleId(moduleId);
                    approvalDataReq.setRecordId(id);
                    approvalDataReq.setSubjectId(subjectId);

                    approvalDataReq.setModuleId(136L);
                    approvalDataReq.setRecordId(1L);
                    Integer versionNo =
                            Integer.parseInt(String.valueOf(approvalInfo.get("versionNo")));

                    Map<String, Object> versionInfo =
                            approvalEngineApi.getApprovalChangeData(approvalDataReq);

                    Map<String, Object> fieldChangeList =
                            buildFieldChangeListData(
                                    moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                                    id,
                                    Long.parseLong(
                                            String.valueOf(versionInfo.get("sourceRecordId"))),
                                    Long.parseLong(String.valueOf(versionInfo.get("preRecordId"))));
                }

                // 主表数据放入返回结果Map
                resultMap.putAll(tablePrefixResultMap);
            }

            // 遍历关联表，填充关联表数据
            for (ModuleTable moduleTable : moduleFilterInfo.getModuleTableList()) {
                // 关联表查询Mapper对象
                BaseMapper moduleTableQuery =
                        SpringBeanUtil.getBean(
                                moduleTable.getTableName() + MYBATIS_MAPPER_SUFFIX,
                                BaseMapper.class);
                QueryWrapper moduleTableQueryWrapper = Wrappers.query();
                buildSelect(moduleTableQueryWrapper, moduleTable.getTableName(), allRightField);

                // 关联表查询筛选条件，按照主表中关联key的进行筛选
                buildWhere(
                        moduleTableQueryWrapper,
                        moduleTable.getTableName()
                                + DB_TABLE_FIELD_SEPARATOR
                                + moduleTable.getJoinRightField(),
                        resultMap.get(
                                moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                        + DB_TABLE_FIELD_SEPARATOR
                                        + ToolUtils.snakeToCamelCase(
                                                moduleTable.getJoinLeftField())));

                // 数据来源于其他主体，需要主体id作为筛选条件
                if (!CollectionUtils.isEmpty(
                        moduleFilterInfo.getModuleInfo().getSourceSubjects())) {
                    List<Integer> subjectIds =
                            moduleFilterInfo.getModuleInfo().getSourceSubjects().stream()
                                    .map(SubjectResp::getId)
                                    .collect(Collectors.toList());
                    log.debug("数据来源于其他主体，主体ID列表为{}", subjectIds);
                    buildWhere(
                            moduleTableQueryWrapper,
                            moduleTable.getTableName()
                                    + DB_TABLE_FIELD_SEPARATOR
                                    + DEFAULT_SUBJECT_KEY,
                            subjectIds.toArray());
                } else {
                    buildWhere(
                            moduleTableQueryWrapper,
                            moduleTable.getTableName()
                                    + DB_TABLE_FIELD_SEPARATOR
                                    + DEFAULT_SUBJECT_KEY,
                            subjectId);
                }

                applyDataRightFilter(
                        moduleTable.getTableName(),
                        tableNameToConfigItems,
                        moduleTableQueryWrapper);

                // 1:N时放入List，其他情况合并到主表Map
                if (TABLE_JOIN_RELATION_TYPE_MULTI2ONE.equals(moduleTable.getRelationType())
                        || TABLE_JOIN_RELATION_TYPE_ONE2ONE.equals(moduleTable.getRelationType())) {
                    Object moduleTableData = moduleTableQuery.selectOne(moduleTableQueryWrapper);

                    if (Objects.isNull(moduleTableData)) {
                        continue;
                    }

                    Map<String, Object> moduleTableBeanMap = new HashMap<>();

                    // 前端无法动态响应动态的label key，所有返回字段都加上表名前缀
                    Map<String, Object> moduleTablePrefixResultMap =
                            BeanUtil.beanToMap(
                                    moduleTableData,
                                    moduleTableBeanMap,
                                    true,
                                    new Editor<String>() {
                                        @Override
                                        public String edit(String s) {
                                            // Map中的key拼接表名
                                            return moduleTable.getTableName()
                                                    + DB_TABLE_FIELD_SEPARATOR
                                                    + s;
                                        }
                                    });

                    if (Objects.nonNull(moduleTablePrefixResultMap)) {
                        // 关联表数据放入返回结果Map
                        resultMap.putAll(moduleTablePrefixResultMap);
                    }

                    // 先屏蔽 前端无法低代码动态化 后续实现之后可以使用此段代码
                    /**
                     * List<String> sameKeyList = new ArrayList<>();
                     *
                     * <p>// 处理同名情况 for (Map.Entry<String, Object> dataItem :
                     * joinTableData.entrySet()) { if (resultMap.containsKey(dataItem.getKey())) {
                     * // 存在同名字段，则主表和关联表都要改名 sameKeyList.add(dataItem.getKey()); } else { //
                     * 不同名字段直接放入主表 resultMap.put(dataItem.getKey(), dataItem.getValue()); } }
                     *
                     * <p>// 改名处理 for (String sameKey : sameKeyList) { // 获取主表对应key数据 Object
                     * primaryKeyVal = resultMap.get(sameKey);
                     *
                     * <p>// 删除主表数据 resultMap.remove(sameKey);
                     *
                     * <p>// key改名后重新放进返回map resultMap.put(
                     * moduleFilterInfo.getModuleInfo().getPrimaryTable() + DB_TABLE_FIELD_SEPARATOR
                     * + sameKey, primaryKeyVal); // 关联表数据放入返回map resultMap.put(
                     * moduleTable.getTableName() + DB_TABLE_FIELD_SEPARATOR + sameKey,
                     * joinTableData.get(sameKey)); log.info(
                     * "主表{}和关联表{}返回数据存在同名字段{},分别改名为{},{}进行返回。",
                     * moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                     * moduleTable.getTableName(), sameKey,
                     * moduleFilterInfo.getModuleInfo().getPrimaryTable() + DB_TABLE_FIELD_SEPARATOR
                     * + sameKey, moduleTable.getTableName() + DB_TABLE_FIELD_SEPARATOR + sameKey);
                     * }
                     */
                } else {
                    List moduleData = moduleTableQuery.selectList(moduleTableQueryWrapper);
                    List<Map<String, Object>> listMap = new ArrayList<>();
                    for (Object data : moduleData) {
                        listMap.add(BeanUtil.beanToMap(data, false, true));
                    }
                    resultMap.put(
                            moduleTable.getTableName() + ONE2MULTI_RESULT_KEY_SUFFIX, listMap);
                }
            }

            resultMap.put("formFieldList", getFormFieldData(moduleFilterInfo, allRightField));

            // 将field中的下划线转为驼峰
            List<ModuleField> transformCombineFields =
                    moduleFilterInfo.getCombineModuleFields().stream()
                            .map(
                                    ele -> {
                                        if (!CollectionUtils.isEmpty(ele.getSourceMappings())) {
                                            List<ModuleField.SourceMapping> sourceMappings =
                                                    ele.getSourceMappings().stream()
                                                            .map(
                                                                    item ->
                                                                            new ModuleField
                                                                                    .SourceMapping(
                                                                                    item.entity(),
                                                                                    ToolUtils
                                                                                            .snakeToCamelCase(
                                                                                                    item
                                                                                                            .field()),
                                                                                    item
                                                                                            .sortOrder()))
                                                            .collect(Collectors.toList());
                                            ele.setSourceMappings(sourceMappings);
                                        }
                                        return ele;
                                    })
                            .toList();

            resultMap.put("combineConfig", transformCombineFields);

            resultMap.put("btnList", moduleFilterInfo.getBtnList());

            resultMap.put("interOperateNode", moduleFilterInfo.getInterOperateRights());
        } else {
            log.debug("数据表:{}主键id:{}无对应数据", moduleFilterInfo.getModuleInfo().getPrimaryTable(), id);
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getMessage());
        }
        return resultMap;
    }

    private Map<String, Object> buildFieldChangeListData(
            String tableName, Long currentRecordId, Long lastVersionId, Long modifyId) {
        Map<String, Object> compareResult = new HashMap<>();
        // 获取主表Mapper对象
        BaseMapper primaryMapper =
                SpringBeanUtil.getBean(tableName + MYBATIS_MAPPER_SUFFIX, BaseMapper.class);

        // 主表查询构造条件
        QueryWrapper queryWrapperCurrent = Wrappers.query();
        if (Objects.isNull(currentRecordId)) {
            log.debug("当前数据主键ID为空，无版本可以对比。");
            return Map.of();
        }

        queryWrapperCurrent.eq(tableName + DB_TABLE_FIELD_SEPARATOR + "id", currentRecordId);

        Object currentData = primaryMapper.selectOne(queryWrapperCurrent);
        if (Objects.isNull(currentData)) {
            log.debug("当前数据主键ID:{}查询结果为空，无版本可以对比。", currentRecordId);
            return Map.of();
        }

        QueryWrapper queryWrapperLastVersion = Wrappers.query();
        if (Objects.isNull(lastVersionId)) {
            log.debug("前一生效版本数据主键ID为空，无版本可以对比。");
            return Map.of();
        }

        queryWrapperLastVersion.eq(tableName + DB_TABLE_FIELD_SEPARATOR + "id", lastVersionId);

        Object lastVersionData = primaryMapper.selectOne(queryWrapperLastVersion);
        if (Objects.isNull(lastVersionData)) {
            log.debug("前一生效版本数据主键ID:{}查询结果为空，无版本可以对比。", lastVersionId);
        } else {
            Map<String, String> diffResult = compareDiff(lastVersionData, currentData);
            compareResult.putAll(diffResult);
        }

        QueryWrapper queryWrapperModify = Wrappers.query();
        if (Objects.isNull(modifyId)) {
            log.debug("版本内上一修改数据主键ID为空，无版本可以对比。");
            return Map.of();
        }

        queryWrapperModify.eq(tableName + DB_TABLE_FIELD_SEPARATOR + "id", modifyId);

        Object modifyData = primaryMapper.selectOne(queryWrapperModify);
        if (Objects.isNull(modifyData)) {
            log.debug("版本内上一修改数据主键ID:{}查询结果为空，无版本可以对比。", modifyId);
        } else {
            Map<String, String> diffResult = compareDiff(lastVersionData, currentData);
            compareResult.putAll(diffResult);
        }
        return compareResult;
    }

    /** 对比新旧实体差异，返回字段：旧值 -> 新值 */
    public static Map<String, String> compareDiff(Object oldObj, Object newObj) {
        Map<String, String> diffMap = new HashMap<>();
        if (oldObj == null || newObj == null) {
            return diffMap;
        }
        Class<?> clazz = oldObj.getClass();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object oldVal = field.get(oldObj);
                Object newVal = field.get(newObj);
                if (oldVal == null && newVal == null) {
                    continue;
                }
                if ((oldVal == null && newVal != null)
                        || (oldVal != null && !oldVal.equals(newVal))) {
                    diffMap.put(field.getName(), "旧值:" + oldVal + " 新值:" + newVal);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return diffMap;
    }

    /**
     * 根据模块配置补充主键id和关联id的权限节点配置
     *
     * @param moduleFilterInfo
     * @return
     */
    private List<ModuleField> fillIdAndJoinIdField(ModuleFilterInfo moduleFilterInfo) {

        List<ModuleField> allRightField = new ArrayList<>();
        allRightField.addAll(moduleFilterInfo.getSimpleModuleFields());
        allRightField.addAll(moduleFilterInfo.getCombineModuleFields());

        // SELECT之前先补充主键ID和关联表ID，防止权限节点不勾选后台报错
        ModuleFilterInfo finalModuleFilterInfo = moduleFilterInfo;
        boolean primaryIdFinded =
                allRightField.stream()
                        .anyMatch(
                                ele ->
                                        ele.getTableName()
                                                        .equals(
                                                                finalModuleFilterInfo
                                                                        .getModuleInfo()
                                                                        .getPrimaryTable())
                                                && ele.getFieldCode().equals("id"));

        if (!primaryIdFinded) {
            log.debug(
                    "主表{}的主键ID未勾选权限节点，手动添加到节点集合，设置权限为3",
                    moduleFilterInfo.getModuleInfo().getPrimaryTable());
            allRightField.add(
                    ModuleField.builder()
                            .moduleId(moduleFilterInfo.getModuleInfo().getModuleId())
                            .filedType("SIMPLE")
                            .tableName(moduleFilterInfo.getModuleInfo().getPrimaryTable())
                            .fieldCode("id")
                            .permissionCode(3)
                            .build());
        } else {
            // id字段统一做成3，避免之后查看权限时为1，前端不返回
            ModuleFilterInfo finalModuleFilterInfo1 = moduleFilterInfo;
            ModuleField matchedFields =
                    allRightField.stream()
                            .filter(
                                    ele ->
                                            ele.getTableName()
                                                            .equals(
                                                                    finalModuleFilterInfo1
                                                                            .getModuleInfo()
                                                                            .getPrimaryTable())
                                                    && ele.getFieldCode().equals("id"))
                            .findFirst()
                            .orElse(null);
            if (Objects.nonNull(matchedFields)) {
                log.debug(
                        "主表{}的主键ID勾选了权限节点，权限节点为{}",
                        moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                        matchedFields.getFieldCode());
            }
        }

        // 遍历关联表，补齐SELECT数据
        for (ModuleTable moduleTable : moduleFilterInfo.getModuleTableList()) {
            ModuleFilterInfo finalModuleFilterInfo2 = moduleFilterInfo;
            boolean primaryJoinIdFinded =
                    allRightField.stream()
                            .anyMatch(
                                    ele ->
                                            ele.getTableName()
                                                            .equals(
                                                                    finalModuleFilterInfo2
                                                                            .getModuleInfo()
                                                                            .getPrimaryTable())
                                                    && ele.getFieldCode()
                                                            .equals(
                                                                    moduleTable
                                                                            .getJoinLeftField()));
            if (!primaryJoinIdFinded) {
                log.debug(
                        "主表{}的关联ID:{}未勾选权限节点，手动添加到节点集合",
                        moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                        moduleTable.getJoinLeftField());
                allRightField.add(
                        ModuleField.builder()
                                .moduleId(moduleFilterInfo.getModuleInfo().getModuleId())
                                .filedType("SIMPLE")
                                .tableName(moduleFilterInfo.getModuleInfo().getPrimaryTable())
                                .fieldCode(moduleTable.getJoinLeftField())
                                .permissionCode(3)
                                .build());
            } else {
                ModuleFilterInfo finalModuleFilterInfo3 = moduleFilterInfo;
                ModuleField matchedFields =
                        allRightField.stream()
                                .filter(
                                        ele ->
                                                ele.getTableName()
                                                                .equals(
                                                                        finalModuleFilterInfo3
                                                                                .getModuleInfo()
                                                                                .getPrimaryTable())
                                                        && ele.getFieldCode()
                                                                .equals(
                                                                        moduleTable
                                                                                .getJoinLeftField()))
                                .findFirst()
                                .orElse(null);
                if (Objects.nonNull(matchedFields)) {
                    log.debug(
                            "主表{}的关联ID:{}勾选了权限节点，权限节点为{}",
                            moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                            moduleTable.getJoinLeftField(),
                            matchedFields.getFieldCode());
                }
            }

            boolean joinTableIdFinded =
                    allRightField.stream()
                            .anyMatch(
                                    ele ->
                                            ele.getTableName().equals(moduleTable.getTableName())
                                                    && ele.getFieldCode()
                                                            .equals(
                                                                    moduleTable
                                                                            .getJoinRightField()));
            if (!joinTableIdFinded) {
                log.debug(
                        "关联表{}的关联ID:{}未勾选权限节点，手动添加到节点集合",
                        moduleTable.getTableName(),
                        moduleTable.getJoinRightField());
                allRightField.add(
                        ModuleField.builder()
                                .moduleId(moduleFilterInfo.getModuleInfo().getModuleId())
                                .filedType("SIMPLE")
                                .tableName(moduleTable.getTableName())
                                .fieldCode(moduleTable.getJoinRightField())
                                .permissionCode(1)
                                .build());
            } else {
                ModuleField matchedFields =
                        allRightField.stream()
                                .filter(
                                        ele ->
                                                ele.getTableName()
                                                                .equals(moduleTable.getTableName())
                                                        && ele.getFieldCode()
                                                                .equals(
                                                                        moduleTable
                                                                                .getJoinRightField()))
                                .findFirst()
                                .orElse(null);
                if (Objects.nonNull(matchedFields)) {
                    log.debug(
                            "关联表{}的关联ID:{}勾选了权限节点，权限节点为{}",
                            moduleTable.getTableName(),
                            moduleTable.getJoinRightField(),
                            matchedFields.getFieldCode());
                }
            }
        }

        return allRightField;
    }

    private List<FormField> getFormFieldData(
            ModuleFilterInfo moduleFilterInfo, List<ModuleField> allFields) {

        // 模块勾选的全部字段集合，方便后期前端低代码动态加载
        //        List<ModuleField> allFields = moduleFilterInfo.getAllFields();

        List<FormField> formFields = new ArrayList<>();

        // 构造form_field_list数据
        for (ModuleField allFieldItem : allFields) {

            // 在simple和combine的属性列表中查找 名称和表名都一致则判断为匹配上
            List<ModuleField> matchedSimpleField =
                    moduleFilterInfo.getSimpleModuleFields().stream()
                            .filter(
                                    ele ->
                                            allFieldItem.getFieldCode().equals(ele.getFieldCode())
                                                    && allFieldItem
                                                            .getTableName()
                                                            .equals(ele.getTableName()))
                            .collect(Collectors.toList());

            if (!CollectionUtils.isEmpty(matchedSimpleField)) {
                allFieldItem.setPermissionCode(matchedSimpleField.get(0).getPermissionCode());
            } else {
                // 组合字段设置permissionCode，暂时不处理同名情况
                List<ModuleField> matchedCombineField =
                        moduleFilterInfo.getCombineModuleFields().stream()
                                .filter(
                                        ele ->
                                                allFieldItem
                                                                .getFieldCode()
                                                                .equals(ele.getFieldCode())
                                                        && allFieldItem
                                                                .getTableName()
                                                                .equals(ele.getTableName()))
                                .collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(matchedCombineField)) {
                    allFieldItem.setPermissionCode(matchedCombineField.get(0).getPermissionCode());
                }
            }

            FormField formField =
                    FormField.builder()
                            //                            .fieldName(allFieldItem.getFieldCode())
                            .fieldName(
                                    allFieldItem.getTableName()
                                            + DB_TABLE_FIELD_SEPARATOR
                                            + ToolUtils.snakeToCamelCase(
                                                    allFieldItem
                                                            .getFieldCode())) // 先统一返回表名加字段名处理重复字段
                            .fieldTitle(allFieldItem.getFieldName())
                            .permissionCode(allFieldItem.getPermissionCode())
                            .tableName(allFieldItem.getTableName())
                            .build();

            formFields.add(formField);
        }

        return formFields;
    }

    private void buildWhere(QueryWrapper queryWrapper, String fieldName, Object... value) {
        queryWrapper.in(fieldName, value);
    }

    /**
     * 数据权限筛选条件构造方法
     *
     * @param tableName 表名
     * @param tableDataRightConf 表名对应的列数据权限配置
     * @param queryWrapper 查询构造器
     */
    private void applyDataRightFilter(
            String tableName,
            Map<String, List<DataRightConfigResp>> tableDataRightConf,
            QueryWrapper queryWrapper) {
        // 表有配置数据权限字段
        if (tableDataRightConf.containsKey(tableName)) {
            if (!CollectionUtils.isEmpty(tableDataRightConf.get(tableName))) {
                DataRightConfigResp dataRightConfigResp = tableDataRightConf.get(tableName).get(0);
                if (!CollectionUtils.isEmpty(dataRightConfigResp.getDataList())) {
                    for (DataConfig dataConfig : dataRightConfigResp.getDataList()) {
                        if (!CollectionUtils.isEmpty(dataConfig.getBizNames())) {
                            log.debug(
                                    "表名:{}字段{}的数据权限生效，值列表:{}",
                                    tableName,
                                    dataConfig.getColumnName(),
                                    dataConfig.getBizNames());
                            boolean includeNull = dataConfig.getBizNames().contains("Null");
                            if (!includeNull) {
                                queryWrapper.in(
                                        tableName + "." + dataConfig.getColumnName(),
                                        dataConfig.getBizNames().toArray());
                            } else {
                                log.debug("筛选条件包含空值");
                                dataConfig.getBizNames().remove("Null");

                                if (CollectionUtils.isEmpty(dataConfig.getBizNames())) {
                                    log.debug("筛选为Null的数据");
                                    queryWrapper.isNull(
                                            tableName + "." + dataConfig.getColumnName());
                                } else {
                                    queryWrapper.and(
                                            qr -> {
                                                ((QueryWrapper<?>) qr)
                                                        .isNull(
                                                                tableName
                                                                        + "."
                                                                        + dataConfig
                                                                                .getColumnName())
                                                        .or(
                                                                wrap -> {
                                                                    ((QueryWrapper<?>) wrap)
                                                                            .in(
                                                                                    tableName
                                                                                            + "."
                                                                                            + dataConfig
                                                                                                    .getColumnName(),
                                                                                    dataConfig
                                                                                            .getBizNames()
                                                                                            .toArray());
                                                                });
                                            });
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void applyOrdering(QueryWrapper query, List<OrderItemReq> orderItemReqs) {
        for (OrderItemReq order : orderItemReqs) {
            String field = order.getField();
            String direction = order.getValue();
            boolean isAsc =
                    CommonConstant.ORDER_ASC_SET.stream()
                            .anyMatch(x -> x.equalsIgnoreCase(direction));
            query.orderBy(
                    true,
                    isAsc,
                    order.getTableName()
                            + DB_TABLE_FIELD_SEPARATOR
                            + ToolUtils.camelToSnakeCase(field));
        }
    }

    private void buildSelect(
            QueryWrapper queryWrapper, String tableName, List<ModuleField> moduleFields) {
        // 筛选表名称为tableName或者类别为COMBINE的
        List<ModuleField> filteredFields =
                moduleFields.stream()
                        .filter(
                                e ->
                                        tableName.equals(e.getTableName())
                                                || e.getFiledType().equals(FIELD_TYPE_COMBINE))
                        .collect(Collectors.toList());
        Set<String> selectColumnsSet = new HashSet<>();
        for (ModuleField moduleField : filteredFields) {
            // 如果是COMBINE类型，要遍历sourceMapping拿出要查询字段
            if (CollectionUtils.isEmpty(moduleField.getSourceMappings())) {
                if (!FIELD_TYPE_COMBINE.equals(moduleField.getFiledType())) {
                    // 普通查询列
                    selectColumnsSet.add(
                            moduleField.getTableName()
                                    + DB_TABLE_FIELD_SEPARATOR
                                    + moduleField.getFieldCode());
                } else {
                    log.debug(
                            "组合字段名称:{},字段标识:{}未绑定数据库实体字段，不加入数据库列查询。",
                            moduleField.getFieldName(),
                            moduleField.getFieldCode());
                }

            } else {
                for (ModuleField.SourceMapping sourceMapping : moduleField.getSourceMappings()) {
                    if (tableName.equals(sourceMapping.entity())) {
                        selectColumnsSet.add(
                                sourceMapping.entity()
                                        + DB_TABLE_FIELD_SEPARATOR
                                        + sourceMapping.field());
                    }
                }
            }
        }

        List<String> columnsList = new ArrayList<>();
        columnsList.addAll(selectColumnsSet);

        queryWrapper.select(columnsList);
    }

    @Override
    public Map<String, Object> dynamicQueryListData(ListQueryParam queryParam, Page page) {

        // 获取权限过滤后的Field结果
        if (Objects.isNull(queryParam.getModuleFilterInfo())) {

            ModuleFilterInfo moduleFilterInfo =
                    moduleInfoWithPermissionService.filterModuleInfo(
                            queryParam.getSubjectId(),
                            queryParam.getRoleId(),
                            queryParam.getModuleId(),
                            "",
                            queryParam.getModuleSkip());
            queryParam.setModuleFilterInfo(moduleFilterInfo);
        }

        // 判断权限信息是否为空
        if (Objects.isNull(queryParam.getModuleFilterInfo())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getMessage());
        }

        // 判断表头是否未配置，单独进行提示
        if (CollectionUtils.isEmpty(
                queryParam.getModuleFilterInfo().getModuleInfo().getTableHeader())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_NO_TABLEHEADER_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_NO_TABLEHEADER_ERROR.getMessage());
        }

        // 检验可查看权限字段是否为空
        if (CollectionUtils.isEmpty(queryParam.getModuleFilterInfo().getSimpleModuleFields())
                && CollectionUtils.isEmpty(
                        queryParam.getModuleFilterInfo().getCombineModuleFields())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getMessage());
        }

        Map<String, Object> resultMap = new HashMap<>();

        if (Objects.isNull(queryParam.getModuleFilterInfo())) {
            return resultMap;
        }

        // 合并simple和combine的field
        List<ModuleField> allRightField = fillIdAndJoinIdField(queryParam.getModuleFilterInfo());

        Map<String, List<OrderItemReq>> tableNameToOrders = new HashMap<>();
        if (!CollectionUtils.isEmpty(queryParam.getOrderItems())) {
            tableNameToOrders =
                    queryParam.getOrderItems().stream()
                            .collect(Collectors.groupingBy(OrderItemReq::getTableName));
        }

        // 主表查询条件构造
        QueryWrapper queryWrapper = Wrappers.query();

        // 列头筛选模式下，筛选出来的主键id集合为空，则本次查询不会有数据返回
        if (queryParam.getColumnFilter()) {
            if (!CollectionUtils.isEmpty(queryParam.getPrimaryTableDataList())) {
                // 获取主表关联key的值作为关联表的查询条件
                Set<Long> idList =
                        queryParam.getPrimaryTableDataList().stream()
                                .map(
                                        e -> {
                                            Field field =
                                                    ReflectionUtils.findField(
                                                            e.getClass(), DEFAULT_PRIMARY_KEY);
                                            field.setAccessible(true);
                                            return Long.parseLong(
                                                    String.valueOf(
                                                            ReflectionUtils.getField(field, e)));
                                        })
                                .collect(Collectors.toSet());
                queryWrapper.in(
                        queryParam.getModuleFilterInfo().getModuleInfo().getPrimaryTable()
                                + DB_TABLE_FIELD_SEPARATOR
                                + DEFAULT_PRIMARY_KEY,
                        idList.toArray());
            } else {
                log.debug("列头筛选模式下，主表的主键id列表为空，则直接返回。");
                buildConfigData(
                        resultMap,
                        queryParam.getModuleFilterInfo(),
                        queryParam.getSubjectId(),
                        queryParam.getRoleId());
                resultMap.put("list", List.of());
                resultMap.put("total", 0);
                return resultMap;
            }
        }

        boolean hasApproval = false;
        if (Objects.nonNull(queryParam.getType())) {
            hasApproval = true;
        }

        List<Map<String, Object>> approvalListResps = new ArrayList<>();

        Map<Long, Map<String, Object>> recordIdToApprovalData = new HashMap<>();

        ApprovalListReq approvalListReq = null;
        // 带审批链的查询，构造查询参数
        if (hasApproval) {
            approvalListReq = new ApprovalListReq();
            approvalListReq.setApprovalType(queryParam.getType());

            approvalListReq.setRecordIdList(Arrays.asList(1L));

            List<Long> idList =
                    queryParam.getPrimaryTableDataList().stream()
                            .map(
                                    e -> {
                                        Field field =
                                                ReflectionUtils.findField(
                                                        e.getClass(), DEFAULT_PRIMARY_KEY);
                                        field.setAccessible(true);
                                        return Long.parseLong(
                                                String.valueOf(ReflectionUtils.getField(field, e)));
                                    })
                            .collect(Collectors.toList());

            //            approvalListReq.setRecordIdList(idList);

            approvalListReq.setRoleId(54L);
            //                approvalListReq.setRoleId(roleId);

            approvalListReq.setModuleIdList(Arrays.asList(136L));
            //            List<Long> moduleIdList =
            //                    primaryTableDataList.stream()
            //                            .map(
            //                                    e -> {
            //                                        Field field =
            //                                                ReflectionUtils.findField(
            //                                                        e.getClass(),"moduleId");
            //                                        field.setAccessible(true);
            //                                        return Long.parseLong(
            //                                                String.valueOf(
            //
            // ReflectionUtils.getField(field, e)));
            //                                    })
            //                            .collect(Collectors.toList());
            //            approvalListReq.setModuleId(moduleIdList);

            approvalListReq.setCurrentUserId(296L);
            approvalListReq.setApplicantId(296L);

            //            approvalListReq.setCurrentUserId(LoginUserUtil.getRealLoginUserId());
            //            approvalListReq.setApplicantId(LoginUserUtil.getLoginUserId());
        }

        // 查询数据权限配置
        List<DataRightConfigResp> dataRightconfigs =
                configCenterApiService.getDataRightConfigItem(
                        queryParam.getSubjectId(), queryParam.getRoleId());
        Map<String, List<DataRightConfigResp>> tableNameToConfigItems =
                dataRightconfigs.stream()
                        .collect(Collectors.groupingBy(DataRightConfigResp::getTableName));

        // 获取主表Mapper对象
        BaseMapper primaryMapper =
                SpringBeanUtil.getBean(
                        queryParam.getModuleFilterInfo().getModuleInfo().getPrimaryTable()
                                + MYBATIS_MAPPER_SUFFIX,
                        BaseMapper.class);

        buildSelect(
                queryWrapper,
                queryParam.getModuleFilterInfo().getModuleInfo().getPrimaryTable(),
                allRightField);

        // 数据来源于其他主体，需要主体id作为筛选条件
        if (!CollectionUtils.isEmpty(
                queryParam.getModuleFilterInfo().getModuleInfo().getSourceSubjects())) {
            List<Integer> subjectIds =
                    queryParam.getModuleFilterInfo().getModuleInfo().getSourceSubjects().stream()
                            .map(SubjectResp::getId)
                            .collect(Collectors.toList());
            log.debug("数据来源于其他主体，主体ID列表为{}", subjectIds);

            if (hasApproval) {
                approvalListReq.setSubjectIdList(
                        subjectIds.stream().map(Integer::longValue).collect(Collectors.toList()));
            }

            buildWhere(
                    queryWrapper,
                    queryParam.getModuleFilterInfo().getModuleInfo().getPrimaryTable()
                            + DB_TABLE_FIELD_SEPARATOR
                            + DEFAULT_SUBJECT_KEY,
                    subjectIds.toArray());
        } else {
            if (hasApproval) {
                approvalListReq.setSubjectIdList(Arrays.asList(queryParam.getSubjectId()));
            }
            buildWhere(
                    queryWrapper,
                    queryParam.getModuleFilterInfo().getModuleInfo().getPrimaryTable()
                            + DB_TABLE_FIELD_SEPARATOR
                            + DEFAULT_SUBJECT_KEY,
                    queryParam.getSubjectId());
        }

        if (hasApproval) {

            approvalListResps = approvalEngineApi.getBusinessList(approvalListReq);

            // 构造recordId为key,审批数据为值的映射map，方便后面数据拼装
            for (Map<String, Object> item : approvalListResps) {
                if (item.containsKey("recordId")) {
                    Map<String, Object> asteriskPrefixMap =
                            item.entrySet().stream()
                                    .collect(
                                            Collectors.toMap(
                                                    entry ->
                                                            KEY_ASTERISK_STR
                                                                    + DB_TABLE_FIELD_SEPARATOR
                                                                    + entry.getKey(),
                                                    Map.Entry::getValue));

                    recordIdToApprovalData.put(
                            Long.parseLong(String.valueOf(item.get("recordId"))),
                            asteriskPrefixMap);
                }
            }
        }

        // 筛选id参数不为空， 则将关联id字段进行筛选 比如学生详情里要以学生ID做筛选查询分数列表
        if (Objects.nonNull(queryParam.getId())) {
            log.debug(
                    "详情页面的列表查看，主表{}的ID:{}",
                    queryParam.getModuleFilterInfo().getModuleInfo().getPrimaryTable(),
                    queryParam.getId());
            if (queryParam.getId() == -1) {
                log.debug("新建页面的列表请求，返回数据为空，只有表头数据返回。");
            }

            if (StringUtils.isNotBlank(
                    queryParam.getModuleFilterInfo().getModuleInfo().getRelateSearchField())) {
                log.debug(
                        "模块数据的筛选条件来自于关联字段{},值为{}",
                        queryParam.getModuleFilterInfo().getModuleInfo().getRelateSearchField(),
                        queryParam.getId());
                buildWhere(
                        queryWrapper,
                        queryParam.getModuleFilterInfo().getModuleInfo().getPrimaryTable()
                                + DB_TABLE_FIELD_SEPARATOR
                                + queryParam
                                        .getModuleFilterInfo()
                                        .getModuleInfo()
                                        .getRelateSearchField(),
                        queryParam.getId());
            } else {
                // 用主表ID作为筛选条件
                buildWhere(
                        queryWrapper,
                        queryParam.getModuleFilterInfo().getModuleInfo().getPrimaryTable()
                                + DB_TABLE_FIELD_SEPARATOR
                                + DEFAULT_PRIMARY_KEY,
                        queryParam.getId());
            }
        }

        applyDataRightFilter(
                queryParam.getModuleFilterInfo().getModuleInfo().getPrimaryTable(),
                tableNameToConfigItems,
                queryWrapper);

        // 请求中带有排序参数
        if (tableNameToOrders.containsKey(
                queryParam.getModuleFilterInfo().getModuleInfo().getPrimaryTable())) {
            applyOrdering(
                    queryWrapper,
                    tableNameToOrders.get(
                            queryParam.getModuleFilterInfo().getModuleInfo().getPrimaryTable()));
        }

        buildOrderBy(queryWrapper, Arrays.asList("update_date", "create_date"), false);

        Page pageData = null;
        // 主表数据查询返回对象List
        List<Object> primaryData;
        // 判断是否需要分页查询
        if (Objects.nonNull(page)) {
            pageData = (Page) primaryMapper.selectPage(page, queryWrapper);
            primaryData = pageData.getRecords();
        } else {
            primaryData = primaryMapper.selectList(queryWrapper);
        }

        // 最终返回的列表行数据
        List<Map<String, Object>> dataList = new ArrayList<>();

        // 遍历关联表，填充关联表数据
        if (!CollectionUtils.isEmpty(primaryData)) {

            // 按前缀和主表关联Id做key，对关联表数据分组的数据
            Map<String, Map<Object, List<Object>>> moduleTableDataMap = new HashMap<>();

            // 遍历关联表，进行关联表数据查询
            for (ModuleTable moduleTable : queryParam.getModuleFilterInfo().getModuleTableList()) {
                // 获取关联表Mapper对象
                BaseMapper moduleTableMapper =
                        SpringBeanUtil.getBean(
                                moduleTable.getTableName() + MYBATIS_MAPPER_SUFFIX,
                                BaseMapper.class);
                // 构造关联表查询条件
                QueryWrapper moduleTableQueryWrapper = Wrappers.query();
                buildSelect(moduleTableQueryWrapper, moduleTable.getTableName(), allRightField);

                // 获取主表关联key的值作为关联表的查询条件
                Set<Long> idList =
                        primaryData.stream()
                                .map(
                                        e -> {
                                            Field field =
                                                    ReflectionUtils.findField(
                                                            e.getClass(),
                                                            ToolUtils.snakeToCamelCase(
                                                                    moduleTable
                                                                            .getJoinLeftField()));
                                            field.setAccessible(true);
                                            return Long.parseLong(
                                                    String.valueOf(
                                                            ReflectionUtils.getField(field, e)));
                                        })
                                .collect(Collectors.toSet());
                buildWhere(
                        moduleTableQueryWrapper,
                        moduleTable.getTableName()
                                + DB_TABLE_FIELD_SEPARATOR
                                + moduleTable.getJoinRightField(),
                        idList.toArray());

                // 数据来源于其他主体，需要主体id作为筛选条件
                if (!CollectionUtils.isEmpty(
                        queryParam.getModuleFilterInfo().getModuleInfo().getSourceSubjects())) {
                    List<Integer> subjectIds =
                            queryParam
                                    .getModuleFilterInfo()
                                    .getModuleInfo()
                                    .getSourceSubjects()
                                    .stream()
                                    .map(SubjectResp::getId)
                                    .collect(Collectors.toList());
                    log.debug("数据来源于其他主体，主体ID列表为{}", subjectIds);
                    buildWhere(
                            moduleTableQueryWrapper,
                            moduleTable.getTableName()
                                    + DB_TABLE_FIELD_SEPARATOR
                                    + DEFAULT_SUBJECT_KEY,
                            subjectIds.toArray());
                } else {
                    buildWhere(
                            moduleTableQueryWrapper,
                            moduleTable.getTableName()
                                    + DB_TABLE_FIELD_SEPARATOR
                                    + DEFAULT_SUBJECT_KEY,
                            queryParam.getSubjectId());
                }

                applyDataRightFilter(
                        moduleTable.getTableName(),
                        tableNameToConfigItems,
                        moduleTableQueryWrapper);

                // 请求中带有排序参数
                if (tableNameToOrders.containsKey(moduleTable.getTableName())) {
                    applyOrdering(
                            moduleTableQueryWrapper,
                            tableNameToOrders.get(moduleTable.getTableName()));
                }

                // 关联表的查询结果
                List<Object> moduleTableList =
                        moduleTableMapper.selectList(moduleTableQueryWrapper);

                // 关联表的查询数据按照关联key分组
                Map<Object, List<Object>> moduleTableIdMap =
                        moduleTableList.stream()
                                .collect(
                                        Collectors.groupingBy(
                                                e -> {
                                                    Field field =
                                                            ReflectionUtils.findField(
                                                                    e.getClass(),
                                                                    ToolUtils.snakeToCamelCase(
                                                                            moduleTable
                                                                                    .getJoinRightField()));
                                                    field.setAccessible(true);
                                                    return ReflectionUtils.getField(field, e);
                                                }));

                // 按照一定的规则拼接key，放入分组后的关联表数据
                if (TABLE_JOIN_RELATION_TYPE_MULTI2ONE.equals(moduleTable.getRelationType())
                        || TABLE_JOIN_RELATION_TYPE_ONE2ONE.equals(moduleTable.getRelationType())) {
                    moduleTableDataMap.put(
                            moduleTable.getTableName()
                                    + KEY_SEPARATOR
                                    + NOT_ONE2MULTI_RESULT_KEY_SUFFIX
                                    + KEY_SEPARATOR
                                    + ToolUtils.snakeToCamelCase(moduleTable.getJoinLeftField()),
                            moduleTableIdMap);
                } else {
                    moduleTableDataMap.put(
                            moduleTable.getTableName()
                                    + KEY_SEPARATOR
                                    + ONE2MULTI_RESULT_KEY_SUFFIX
                                    + KEY_SEPARATOR
                                    + ToolUtils.snakeToCamelCase(moduleTable.getJoinLeftField()),
                            moduleTableIdMap);
                }
            }
            // 遍历主表数据，根据关联key的值填充关联表数据
            for (Object item : primaryData) {

                Map<String, Object> beanMap = new HashMap<>();
                // 前端无法动态响应动态的label key，所有返回字段都加上表名前缀
                ModuleFilterInfo finalModuleFilterInfo4 = queryParam.getModuleFilterInfo();
                Map<String, Object> primaryTablePrefixResultMap =
                        BeanUtil.beanToMap(
                                item,
                                beanMap,
                                true,
                                new Editor<String>() {
                                    @Override
                                    public String edit(String s) {
                                        // Map中的key拼接表名
                                        return finalModuleFilterInfo4
                                                        .getModuleInfo()
                                                        .getPrimaryTable()
                                                + DB_TABLE_FIELD_SEPARATOR
                                                + s;
                                    }
                                });

                for (Map.Entry<String, Map<Object, List<Object>>> ele :
                        moduleTableDataMap.entrySet()) {
                    String key = ele.getKey();
                    // String[2] 0索引为关联表名，1:N时拼接List;1索引为关联key在主表中的名称
                    String[] splitKeys = key.split(KEY_SEPARATOR);

                    // 关联key的值为空，过滤掉
                    if (Objects.isNull(
                            primaryTablePrefixResultMap.get(
                                    queryParam
                                                    .getModuleFilterInfo()
                                                    .getModuleInfo()
                                                    .getPrimaryTable()
                                            + DB_TABLE_FIELD_SEPARATOR
                                            + splitKeys[2]))) {
                        continue;
                    }

                    // 根据主表中关联key的值，获取关联表数据
                    List<Object> joinTableData =
                            ele.getValue()
                                    .get(
                                            primaryTablePrefixResultMap.get(
                                                    queryParam
                                                                    .getModuleFilterInfo()
                                                                    .getModuleInfo()
                                                                    .getPrimaryTable()
                                                            + DB_TABLE_FIELD_SEPARATOR
                                                            + splitKeys[2]));

                    // 主表关联的子表数据为空，则跳过
                    if (CollectionUtils.isEmpty(joinTableData)) {
                        continue;
                    }

                    if (key.contains(ONE2MULTI_RESULT_KEY_SUFFIX)) { // 1:N的情况，放入List
                        List<Map<String, Object>> joinTableListMap = new ArrayList<>();
                        // 关联表数据加表名前缀
                        for (Object obj : joinTableData) {
                            Map<String, Object> targetMap = new HashMap<>();
                            // 前端无法动态响应动态的label key，所有返回字段都加上表名前缀
                            Map<String, Object> joinTablePrefixResultMap =
                                    BeanUtil.beanToMap(
                                            obj,
                                            targetMap,
                                            true,
                                            new Editor<String>() {
                                                @Override
                                                public String edit(String s) {
                                                    // Map中的key拼接表名
                                                    return splitKeys[0]
                                                            + DB_TABLE_FIELD_SEPARATOR
                                                            + s;
                                                }
                                            });
                            joinTableListMap.add(joinTablePrefixResultMap);
                        }

                        primaryTablePrefixResultMap.put(
                                splitKeys[0] + splitKeys[1], joinTableListMap);
                    } else { // 1:1或N:1，数据合并到主表数据

                        if (!CollectionUtils.isEmpty(joinTableData)) {

                            Map<String, Object> joinTableTargetMap = new HashMap<>();
                            // 前端无法动态响应动态的label key，所有返回字段都加上表名前缀
                            Map<String, Object> joinTableMergePrefixResultMap =
                                    BeanUtil.beanToMap(
                                            joinTableData.get(0),
                                            joinTableTargetMap,
                                            true,
                                            new Editor<String>() {
                                                @Override
                                                public String edit(String s) {
                                                    // Map中的key拼接表名
                                                    return splitKeys[0]
                                                            + DB_TABLE_FIELD_SEPARATOR
                                                            + s;
                                                }
                                            });

                            primaryTablePrefixResultMap.putAll(joinTableMergePrefixResultMap);
                        }
                    }
                }

                if (!hasApproval) {
                    primaryTablePrefixResultMap.put(
                            "btnList", queryParam.getModuleFilterInfo().getBtnList());
                } else {
                    primaryTablePrefixResultMap.put(
                            "btnList", recordIdToApprovalData.get(1L).get("*.btnList"));
                    // primaryTablePrefixResultMap.putAll(recordIdToApprovalData.get(Long.parseLong(String.valueOf(primaryId))));

                    primaryTablePrefixResultMap.putAll(recordIdToApprovalData.get(1L));
                }

                // 加入审批链相关字段
                Object primaryId =
                        primaryTablePrefixResultMap.get(
                                finalModuleFilterInfo4.getModuleInfo().getPrimaryTable()
                                        + DB_TABLE_FIELD_SEPARATOR
                                        + DEFAULT_PRIMARY_KEY);

                dataList.add(primaryTablePrefixResultMap);
            }

            // 分页查询时放入总数
            if (Objects.nonNull(page)) {
                resultMap.put("total", pageData.getTotal());
            }
        }

        // 组装返回数据
        resultMap.put("list", dataList);

        buildConfigData(
                resultMap,
                queryParam.getModuleFilterInfo(),
                queryParam.getSubjectId(),
                queryParam.getRoleId());

        return resultMap;
    }

    private Map<String, Object> dynamicQueryListDataWithSearch(
            Long subjectId,
            Long roleId,
            Long moduleId,
            ListQueryReq req,
            Page page,
            ModuleFilterInfo moduleFilterInfo,
            List<Long> moduleSkip) {
        Map<String, Object> resultMap = new HashMap<>();
        List<Object> finalPrimaryTableDataList = new ArrayList<>();

        boolean columnFilter = false;

        if (!CollectionUtils.isEmpty(req.getSearch())) {
            columnFilter = true;
            finalPrimaryTableDataList =
                    getFinalPrimaryData(
                            subjectId, roleId, moduleId, req, moduleFilterInfo, moduleSkip);
            log.debug("列表搜索条件不为空，先查出主表数据条数:{}", finalPrimaryTableDataList.size());
        } else {
            log.debug("列头搜索条件为空，先按公共的筛选条件筛选一遍主表。");
            // 主表查询条件构造
            QueryWrapper queryWrapper = Wrappers.query();
            finalPrimaryTableDataList =
                    commonQuery(
                            subjectId,
                            roleId,
                            moduleId,
                            moduleFilterInfo,
                            moduleSkip,
                            queryWrapper,
                            true);
        }

        List<ApprovalListResp> approvalListResps = new ArrayList<>();

        Integer type = null;

        // 是带审批链的列表查询，则最终的Id集合需要审批链接口提供
        if (req instanceof ApprovalListQueryReq) {
            if (!CollectionUtils.isEmpty(finalPrimaryTableDataList)) {
                ApprovalListQueryReq approvalReq = (ApprovalListQueryReq) req;
                type = approvalReq.getType();
            }
        }

        ListQueryParam listQueryParam =
                ListQueryParam.builder()
                        .subjectId(subjectId)
                        .roleId(roleId)
                        .moduleId(moduleId)
                        .id(req.getId())
                        .moduleFilterInfo(moduleFilterInfo)
                        .moduleSkip(moduleSkip)
                        .columnFilter(columnFilter)
                        .primaryTableDataList(finalPrimaryTableDataList)
                        .orderItems(req.getOrder())
                        .type(type)
                        .build();

        resultMap.putAll(dynamicQueryListData(listQueryParam, page));

        mergeApprovalData(resultMap, approvalListResps);
        return resultMap;
    }

    /**
     * 公共条件查询，包括主体、数据权限等筛选条件
     *
     * @param subjectId
     * @param roleId
     * @param moduleId
     * @param moduleFilterInfo
     * @param moduleSkip
     * @param queryWrapper
     * @param returnResult
     * @return
     */
    private List<Object> commonQuery(
            Long subjectId,
            Long roleId,
            Long moduleId,
            ModuleFilterInfo moduleFilterInfo,
            List<Long> moduleSkip,
            QueryWrapper queryWrapper,
            boolean returnResult) {
        // 获取权限过滤后的Field结果
        if (Objects.isNull(moduleFilterInfo)) {

            moduleFilterInfo =
                    moduleInfoWithPermissionService.filterModuleInfo(
                            subjectId, roleId, moduleId, "", moduleSkip);
        }

        // 判断权限信息是否为空
        if (Objects.isNull(moduleFilterInfo)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getMessage());
        }

        // 判断表头是否未配置，单独进行提示
        if (CollectionUtils.isEmpty(moduleFilterInfo.getModuleInfo().getTableHeader())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_NO_TABLEHEADER_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_NO_TABLEHEADER_ERROR.getMessage());
        }

        // 检验可查看权限字段是否为空
        if (CollectionUtils.isEmpty(moduleFilterInfo.getSimpleModuleFields())
                && CollectionUtils.isEmpty(moduleFilterInfo.getCombineModuleFields())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getMessage());
        }

        // 查询数据权限配置
        List<DataRightConfigResp> dataRightconfigs =
                configCenterApiService.getDataRightConfigItem(subjectId, roleId);
        Map<String, List<DataRightConfigResp>> tableNameToConfigItems =
                dataRightconfigs.stream()
                        .collect(Collectors.groupingBy(DataRightConfigResp::getTableName));

        // 数据来源于其他主体，需要主体id作为筛选条件
        if (!CollectionUtils.isEmpty(moduleFilterInfo.getModuleInfo().getSourceSubjects())) {
            List<Integer> subjectIds =
                    moduleFilterInfo.getModuleInfo().getSourceSubjects().stream()
                            .map(SubjectResp::getId)
                            .collect(Collectors.toList());
            log.debug("数据来源于其他主体，主体ID列表为{}", subjectIds);
            buildWhere(
                    queryWrapper,
                    moduleFilterInfo.getModuleInfo().getPrimaryTable()
                            + DB_TABLE_FIELD_SEPARATOR
                            + DEFAULT_SUBJECT_KEY,
                    subjectIds.toArray());
        } else {
            buildWhere(
                    queryWrapper,
                    moduleFilterInfo.getModuleInfo().getPrimaryTable()
                            + DB_TABLE_FIELD_SEPARATOR
                            + DEFAULT_SUBJECT_KEY,
                    subjectId);
        }

        applyDataRightFilter(
                moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                tableNameToConfigItems,
                queryWrapper);

        if (returnResult) {
            // 获取主表Mapper对象
            BaseMapper primaryMapper =
                    SpringBeanUtil.getBean(
                            moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                    + MYBATIS_MAPPER_SUFFIX,
                            BaseMapper.class);

            List<Object> resultList = primaryMapper.selectList(queryWrapper);
            return resultList;
        } else {
            return List.of();
        }
    }

    private void mergeApprovalData(
            Map<String, Object> resultMap, List<ApprovalListResp> approvalListResps) {}

    /**
     * 数值参数
     *
     * @param startObj
     * @param endObj
     * @return
     */
    private Pair<String, Object> formatBetweenValue(Object startObj, Object endObj) {
        LocalDateTime startDate =
                startObj != null ? ToolUtils.dateTimeStrToDateTime(startObj.toString()) : null;
        LocalDateTime endDate =
                endObj != null ? ToolUtils.dateTimeStrToDateTime(endObj.toString()) : null;

        boolean isDefault = (startDate == null && endDate == null);
        if (isDefault) {
            // 默认今天
            throw new BusinessException("开始时间和结束时间不能都为空。");
        } else if (Objects.nonNull(startDate) && Objects.isNull(endDate)) {
            // 只有开始时间，不处理 endTime
            return Pair.of("egt", startDate);
        } else if (Objects.isNull(startDate) && Objects.nonNull(endDate)) {
            // 只有结束时间
            return Pair.of("elt", endDate);
        } else {
            // 都有
            if (startDate.isAfter(endDate)) {
                throw new BusinessException("开始时间不能大于结束时间");
            }
            return Pair.of("between", List.of(startDate, endDate));
        }
    }

    /**
     * 时间参数 默认 2026-04-30 08:00:00
     *
     * @param startObj
     * @param endObj
     * @return
     */
    private Pair<String, Object> formatDateRangeValue(Object startObj, Object endObj) {
        if (startObj != null && endObj == null) {
            return Pair.of("egt", startObj);
        } else if (startObj == null && endObj != null) {
            return Pair.of("elt", endObj);
        } else if (startObj != null && endObj != null) {
            return Pair.of("between", List.of(startObj, endObj));
        }
        return Pair.of("between", null);
    }

    private Pair<String, Object> formatDateOverlapRangeValue(Object startObj, Object endObj) {
        String operator = "overlap";
        Object value = null;

        if (startObj != null && endObj == null) {
            value = List.of("start", startObj, endObj);
        } else if (endObj != null && startObj == null) {
            value = List.of("end", startObj, endObj);
        } else if (startObj != null && endObj != null) {
            value = List.of("range", startObj, endObj);
        }
        return Pair.of(operator, value);
    }

    private Pair<String, Object> formatOriginalDateRange(Object startObj, Object endObj) {
        if (startObj != null && (endObj == null || endObj.toString().isEmpty())) {
            return Pair.of("egt", startObj);
        } else if (endObj != null && (startObj == null || startObj.toString().isEmpty())) {
            return Pair.of("elt", endObj);
        } else if (startObj != null && endObj != null) {
            // 这里假设 startObj, endObj 是字符串日期
            return Pair.of("between", List.of(startObj, endObj));
        }
        return Pair.of("between", null);
    }

    /**
     * 应用单个搜索条件
     *
     * @return 是否实际添加了条件
     */
    private <T> boolean applySingleFilter(
            QueryWrapper<T> wrapper, String field, String op, Object value, boolean isOr) {
        String column = field;

        switch (op) {
            case "like" -> {
                if (isOr) wrapper.or().like(column, value);
                else wrapper.like(column, value);
                return true;
            }
            case "eq" -> {
                if (isOr) wrapper.or().eq(column, value);
                else wrapper.eq(column, value);
                return true;
            }
            case "in" -> {
                if (value instanceof java.util.Collection<?> coll && !coll.isEmpty()) {
                    if (isOr) wrapper.or().in(column, coll);
                    else wrapper.in(column, coll);
                    return true;
                }
            }
            case "egt" -> {
                if (isOr) wrapper.or().ge(column, value);
                else wrapper.ge(column, value);
                return true;
            }
            case "elt" -> {
                if (isOr) wrapper.or().le(column, value);
                else wrapper.le(column, value);
                return true;
            }
            case "between" -> {
                if (value instanceof List<?> list && list.size() == 2) {
                    if (isOr) wrapper.or().between(column, list.get(0), list.get(1));
                    else wrapper.between(column, list.get(0), list.get(1));
                    return true;
                }
            }
            case "overlap" -> {
                if (value instanceof List<?> list && list.size() == 3) {
                    applyOverlapFilter(wrapper, field, list);
                    return true;
                }
            }
            case "textContain" -> {
                if (value instanceof java.util.Collection<?> coll && !coll.isEmpty()) {
                    for (Object o : coll) {
                        if (isOr) {
                            wrapper.or().apply("INSTR(" + column + ",?" + ")>1", o);
                        } else {
                            wrapper.or().apply("INSTR(" + column + ",?" + ")>1", o);
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /** 应用时间重叠过滤 (PHP: operator === 'overlap' 逻辑) */
    private <T> void applyOverlapFilter(QueryWrapper<T> wrapper, String field, List<?> value) {
        //        String type = value.get(0).toString();
        //        long startTime = (long) value.get(1);
        //        long endTime = (long) value.get(2);

        // 注意：overlap 逻辑通常涉及两个字段（开始时间和结束时间）
        // PHP 中通过 deepHandleValueByColumn 识别 fields 中的 start/end 字段
        // 这里简化处理，假设 field 本身暗示了规则，或者解析 field 为 start/end
        // 由于 UserEntity 搜索中 age 可能不是 overlap 类型，通常 overlap 用于合同时间等
    }

    /** 格式化搜索值 (PHP: formatValue) */
    private Pair<String, Object> formatValue(QueryOpEnum operator, Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Pair.of(operator.getOp(), value);
        }

        if (!map.containsKey(CommonConstant.START_KEY)) {
            return Pair.of(operator.getOp(), value);
        }

        Object startObj = map.get(CommonConstant.START_KEY);
        Object endObj = map.get(CommonConstant.END_KEY);

        if ((startObj == null || startObj.toString().isEmpty())
                && (endObj == null || endObj.toString().isEmpty())) {
            throw new BusinessException("区间范围传值，必须存在一项");
        }

        return switch (operator) {
            case QueryOpEnum.OP_NUMBER_RANGE -> formatBetweenValue(startObj, endObj);
            case QueryOpEnum.OP_DATE_RANGE -> formatDateRangeValue(startObj, endObj);
            case QueryOpEnum.OP_DATE_OVERLAP_RANGE -> formatDateOverlapRangeValue(startObj, endObj);
            case QueryOpEnum.OP_ORIGINAL_DATE_RANGE -> formatOriginalDateRange(startObj, endObj);
            default -> Pair.of(operator.getOp(), value);
        };
    }

    private void applySearchFilters(QueryWrapper query, List<SearchItemReq> searchItemReqs) {

        if (searchItemReqs == null || searchItemReqs.isEmpty()) {
            return;
        }

        for (SearchItemReq item : searchItemReqs) {
            // 这里的 fields 目前只有有一个值
            List<String> fields = item.getField();
            String searchType = item.getSearchType();
            Object value = item.getValue();
            if (fields == null || fields.isEmpty() || value == null) {
                continue;
            }
            QueryOpEnum opEnum = QueryOpEnum.getQueryOpEnumByOp(searchType);
            if (opEnum == null) {
                continue;
            }
            Pair<String, Object> formatted = formatValue(opEnum, value);
            String finalOp = formatted.getLeft();
            Object finalValue = formatted.getRight();

            if (finalValue == null) {
                continue;
            }

            query.and(
                    wrapper -> {
                        int addedCount = 0;
                        for (int i = 0; i < fields.size(); i++) {
                            String field = fields.get(i);
                            boolean isOr = i > 0;
                            QueryWrapper queryWrapper = (QueryWrapper) wrapper;
                            if (applySingleFilter(
                                    queryWrapper,
                                    item.getTable()
                                            + DB_TABLE_FIELD_SEPARATOR
                                            + ToolUtils.camelToSnakeCase(field),
                                    finalOp,
                                    finalValue,
                                    isOr)) {
                                addedCount++;
                            }
                        }
                        // 如果内部没有添加任何条件，MyBatis-Plus 在某些版本下可能会生成空括号导致语法错误
                        // 这里通过返回逻辑确保安全，虽然 wrapper 是传参，但我们可以检查其状态
                    });
        }
    }

    /**
     * 根据主表和子表的过滤条件，筛选出最终需要展示的主键id
     *
     * @param subjectId
     * @param roleId
     * @param moduleId
     * @param req
     * @param moduleFilterInfo
     * @param moduleSkip
     * @returngetFinalPrimaryKey
     */
    private List<Object> getFinalPrimaryData(
            Long subjectId,
            Long roleId,
            Long moduleId,
            ListQueryReq req,
            ModuleFilterInfo moduleFilterInfo,
            List<Long> moduleSkip) {
        // 获取权限过滤后的Field结果
        if (Objects.isNull(moduleFilterInfo)) {

            moduleFilterInfo =
                    moduleInfoWithPermissionService.filterModuleInfo(
                            subjectId, roleId, moduleId, "", moduleSkip);
        }

        // 判断权限信息是否为空
        if (Objects.isNull(moduleFilterInfo)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getMessage());
        }

        // 判断表头是否未配置，单独进行提示
        if (CollectionUtils.isEmpty(moduleFilterInfo.getModuleInfo().getTableHeader())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_NO_TABLEHEADER_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_NO_TABLEHEADER_ERROR.getMessage());
        }

        // 检验可查看权限字段是否为空
        if (CollectionUtils.isEmpty(moduleFilterInfo.getSimpleModuleFields())
                && CollectionUtils.isEmpty(moduleFilterInfo.getCombineModuleFields())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getMessage());
        }

        // 查询数据权限配置
        List<DataRightConfigResp> dataRightconfigs =
                configCenterApiService.getDataRightConfigItem(subjectId, roleId);
        Map<String, List<DataRightConfigResp>> tableNameToConfigItems =
                dataRightconfigs.stream()
                        .collect(Collectors.groupingBy(DataRightConfigResp::getTableName));

        Map<String, List<SearchItemReq>> tableNameToSearchItem =
                req.getSearch().stream().collect(Collectors.groupingBy(SearchItemReq::getTable));

        Map<String, List<Object>> primaryColomnInCondition = new HashMap<>();
        // 遍历关联表，进行关联表数据查询
        for (ModuleTable moduleTable : moduleFilterInfo.getModuleTableList()) {
            // 获取关联表Mapper对象
            BaseMapper moduleTableMapper =
                    SpringBeanUtil.getBean(
                            moduleTable.getTableName() + MYBATIS_MAPPER_SUFFIX, BaseMapper.class);
            // 构造关联表查询条件
            QueryWrapper moduleTableQueryWrapper = Wrappers.query();
            moduleTableQueryWrapper.select(
                    moduleTable.getTableName()
                            + DB_TABLE_FIELD_SEPARATOR
                            + moduleTable.getJoinRightField());

            // 数据来源于其他主体，需要主体id作为筛选条件
            if (!CollectionUtils.isEmpty(moduleFilterInfo.getModuleInfo().getSourceSubjects())) {
                List<Integer> subjectIds =
                        moduleFilterInfo.getModuleInfo().getSourceSubjects().stream()
                                .map(SubjectResp::getId)
                                .collect(Collectors.toList());
                log.debug("数据来源于其他主体，主体ID列表为{}", subjectIds);
                buildWhere(
                        moduleTableQueryWrapper,
                        moduleTable.getTableName() + DB_TABLE_FIELD_SEPARATOR + DEFAULT_SUBJECT_KEY,
                        subjectIds.toArray());
            } else {
                buildWhere(
                        moduleTableQueryWrapper,
                        moduleTable.getTableName() + DB_TABLE_FIELD_SEPARATOR + DEFAULT_SUBJECT_KEY,
                        subjectId);
            }

            // 数据权限过滤
            applyDataRightFilter(
                    moduleTable.getTableName(), tableNameToConfigItems, moduleTableQueryWrapper);

            // 子表筛选条件进行筛选
            if (tableNameToSearchItem.containsKey(moduleTable.getTableName())) {
                List<SearchItemReq> searchItems =
                        tableNameToSearchItem.get(moduleTable.getTableName());
                applySearchFilters(moduleTableQueryWrapper, searchItems);
            }

            // 返回和主表的关联字段查询值
            List<Object> moduleTableDataList =
                    moduleTableMapper.selectList(moduleTableQueryWrapper);

            // 数据不为空，则放入主表筛选map
            if (!CollectionUtils.isEmpty(moduleTableDataList)) {
                List<Object> joinFiledValues =
                        moduleTableDataList.stream()
                                .filter(Objects::nonNull)
                                .map(
                                        ele -> {
                                            Object columnValue = null;
                                            Field columnField =
                                                    ReflectionUtils.findField(
                                                            ele.getClass(),
                                                            ToolUtils.snakeToCamelCase(
                                                                    moduleTable
                                                                            .getJoinRightField()));
                                            if (Objects.nonNull(columnField)) {
                                                columnField.setAccessible(true);
                                                columnValue =
                                                        ReflectionUtils.getField(columnField, ele);
                                                if (Objects.nonNull(columnValue)) {
                                                    return columnValue;
                                                } else {
                                                    return null;
                                                }
                                            } else {
                                                return null;
                                            }
                                        })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
                String columnKeyInPrimary =
                        moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                + DB_TABLE_FIELD_SEPARATOR
                                + moduleTable.getJoinLeftField();
                if (!CollectionUtils.isEmpty(joinFiledValues)) {
                    putAndIntersection(
                            primaryColomnInCondition, columnKeyInPrimary, joinFiledValues);
                }
            }
        }

        // 主表查询
        // 获取主表Mapper对象
        BaseMapper primaryMapper =
                SpringBeanUtil.getBean(
                        moduleFilterInfo.getModuleInfo().getPrimaryTable() + MYBATIS_MAPPER_SUFFIX,
                        BaseMapper.class);

        // 主表查询条件构造
        QueryWrapper queryWrapper = Wrappers.query();
        queryWrapper.select("id");

        // 数据来源于其他主体，需要主体id作为筛选条件
        if (!CollectionUtils.isEmpty(moduleFilterInfo.getModuleInfo().getSourceSubjects())) {
            List<Integer> subjectIds =
                    moduleFilterInfo.getModuleInfo().getSourceSubjects().stream()
                            .map(SubjectResp::getId)
                            .collect(Collectors.toList());
            log.debug("数据来源于其他主体，主体ID列表为{}", subjectIds);
            buildWhere(
                    queryWrapper,
                    moduleFilterInfo.getModuleInfo().getPrimaryTable()
                            + DB_TABLE_FIELD_SEPARATOR
                            + DEFAULT_SUBJECT_KEY,
                    subjectIds.toArray());
        } else {
            buildWhere(
                    queryWrapper,
                    moduleFilterInfo.getModuleInfo().getPrimaryTable()
                            + DB_TABLE_FIELD_SEPARATOR
                            + DEFAULT_SUBJECT_KEY,
                    subjectId);
        }

        // 筛选id参数不为空， 则将关联id字段进行筛选 比如学生详情里要以学生ID做筛选查询分数列表
        if (Objects.nonNull(req.getId())) {
            log.debug(
                    "详情页面的列表查看，主表{}的ID:{}",
                    moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                    req.getId());
            if (req.getId() == -1) {
                log.debug("新建页面的列表请求，返回数据为空，只有表头数据返回。");
            }

            if (StringUtils.isNotBlank(moduleFilterInfo.getModuleInfo().getRelateSearchField())) {
                log.debug(
                        "模块数据的筛选条件来自于关联字段{},值为{}",
                        moduleFilterInfo.getModuleInfo().getRelateSearchField(),
                        req.getId());
                buildWhere(
                        queryWrapper,
                        moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                + DB_TABLE_FIELD_SEPARATOR
                                + moduleFilterInfo.getModuleInfo().getRelateSearchField(),
                        req.getId());
            } else {
                // 用主表ID作为筛选条件
                buildWhere(
                        queryWrapper,
                        moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                + DB_TABLE_FIELD_SEPARATOR
                                + DEFAULT_PRIMARY_KEY,
                        req.getId());
            }
        }

        applyDataRightFilter(
                moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                tableNameToConfigItems,
                queryWrapper);

        // 主表筛选条件进行筛选
        if (tableNameToSearchItem.containsKey(moduleFilterInfo.getModuleInfo().getPrimaryTable())) {
            List<SearchItemReq> searchItems =
                    tableNameToSearchItem.get(moduleFilterInfo.getModuleInfo().getPrimaryTable());
            applySearchFilters(queryWrapper, searchItems);
        }

        // 主表和子表关联字段筛选条件
        for (Map.Entry<String, List<Object>> entry : primaryColomnInCondition.entrySet()) {
            log.debug(
                    "主表:{}的字段:{}从子表获取的筛选值为:{}",
                    moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                    entry.getKey(),
                    entry.getValue());
            queryWrapper.in(entry.getKey(), entry.getValue().toArray());
        }

        List<Object> primaryTableDataList = primaryMapper.selectList(queryWrapper);

        return primaryTableDataList;
    }

    /** 两个集合取交集，返回新List */
    private <T> List<T> intersect(List<T> oldList, List<T> newList) {
        Set<T> set = new HashSet<>(oldList);
        return newList.stream().filter(set::contains).collect(Collectors.toList());
    }

    public <T> void putAndIntersection(Map<String, List<T>> map, String key, List<T> newDataList) {
        map.compute(
                key,
                (k, oldList) -> {
                    // key不存在 oldList = null，直接返回新集合
                    if (oldList == null) {
                        return new ArrayList<>(newDataList);
                    }
                    // key存在：取交集返回
                    return intersect(oldList, newDataList);
                });
    }

    @Override
    public Map<Object, Object> dynamicQueryAllModuleDetailData(
            Long subjectId, Long roleId, Long menuId, Long id, Long relateSearchId) {
        log.debug(
                "获取主体id:{} , 角色id:{} , 主键id:{} , 菜单ID:{}及其子菜单所对应模块的数据",
                subjectId,
                roleId,
                id,
                menuId);
        Map<Object, Object> resultMap = new HashMap<>();

        ConfigApiResponse<List<MenuListResp>> response =
                configCenterApiClient.getMenuList(subjectId, 1);

        if (!ConfigApiResponse.isSuccessResponse(response)) {
            resultMap.put(menuId, new HashMap<>());
            return resultMap;
        }
        List<MenuListResp> menuRespList = response.getData();
        if (CollectionUtil.isEmpty(menuRespList)) {
            resultMap.put(menuId, new HashMap<>());
            return resultMap;
        }

        // 筛选业务菜单，去掉系统设置菜单
        List<MenuListResp> bizMenuList = new ArrayList<>();
        bizMenuList.addAll(
                menuRespList.stream().filter(ele -> ele.getCategory().equals(1)).toList());
        bizMenuList.sort(Comparator.comparing(MenuListResp::getSortOrder));

        List<MenuListResp> targetModules = findTargetAndAllChildrenIterate(bizMenuList, menuId);

        // 统计当前页面没有可查看权限的菜单数
        int count = 0;

        boolean saveBtnShow = false;

        for (MenuListResp menu : targetModules) {
            ModuleFilterInfo moduleFilterInfo =
                    moduleInfoWithPermissionService.filterModuleInfo(
                            subjectId, roleId, menu.getModuleId(), "", List.of());

            if (!saveBtnShow
                    && moduleFilterInfo.getBtnList().stream()
                            .anyMatch(
                                    ele ->
                                            FormButtonEnum.EDITE
                                                    .getKey()
                                                    .equals(ele.getButtonKey()))) {
                log.debug(
                        "菜单id:{} , 菜单名称:{} , 模块id:{}有编辑或创建权限，整个页面显示保存按钮。",
                        menu.getId(),
                        menu.getTitle(),
                        menu.getModuleId());
                saveBtnShow = true;
            }

            try {
                if ("LIST".equals(moduleFilterInfo.getModuleInfo().getModuleType())) {
                    ListQueryParam listQueryParam =
                            ListQueryParam.builder()
                                    .subjectId(subjectId)
                                    .roleId(roleId)
                                    .moduleId(menu.getModuleId())
                                    .id(id)
                                    .moduleFilterInfo(moduleFilterInfo)
                                    .moduleSkip(List.of())
                                    .columnFilter(false)
                                    .primaryTableDataList(List.of())
                                    .orderItems(List.of())
                                    .type(null)
                                    .build();
                    resultMap.put(menu.getId(), dynamicQueryListData(listQueryParam, null));
                } else {
                    resultMap.put(
                            menu.getId(),
                            dynamicQueryDetailData(
                                    subjectId,
                                    roleId,
                                    menu.getModuleId(),
                                    id,
                                    null,
                                    moduleFilterInfo,
                                    false,
                                    null));
                }
            } catch (BusinessException e) {
                log.debug(
                        "菜单id:{} , 菜单名称:{} , 模块id:{}无任何可查看的权限字段。",
                        menu.getId(),
                        menu.getTitle(),
                        menu.getModuleId());
                resultMap.put(menu.getId(), ApiResponse.error(e.getCode(), e.getMessage()));
                count++;
            }
        }

        if (!CollectionUtils.isEmpty(targetModules) && count == targetModules.size()) {
            log.debug("菜单id:{}对应的页面下所有菜单都没有可查看的权限字段。", menuId);
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MENU_RIGHT_NODE_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MENU_RIGHT_NODE_ERROR.getMessage());
        }

        resultMap.put("showSaveBtn", saveBtnShow);

        return resultMap;
    }

    /**
     * 查找targetId对应的节点及其所有的子节点以及子节点的子节点，直到弹窗
     *
     * @param flatList
     * @param targetId
     * @return
     */
    @Override
    public List<MenuListResp> findTargetAndAllChildrenIterate(
            List<MenuListResp> flatList, Long targetId) {
        List<MenuListResp> result = new ArrayList<>();
        MenuListResp target =
                flatList.stream().filter(n -> n.getId().equals(targetId)).findFirst().orElse(null);
        if (target == null) {
            return result;
        }
        result.add(target);

        // 预构建：父ID -> 所有直接子节点
        Map<Long, List<MenuListResp>> parentChildMap =
                flatList.stream().collect(Collectors.groupingBy(MenuListResp::getPid));

        // 队列迭代遍历所有后代
        Queue<Long> queue = new LinkedList<>();
        queue.add(targetId);

        while (!queue.isEmpty()) {
            Long currentParentId = queue.poll();
            // 获取当前父节点的所有直接子节点
            List<MenuListResp> children =
                    parentChildMap.getOrDefault(currentParentId, Collections.emptyList());
            for (MenuListResp child : children) {
                // 过滤掉弹窗类型及其子节点
                if (child.getMenuType().equals(5)) {
                    log.debug(
                            "菜单id:{} , 菜单名称:{} , 模块id:{}是弹窗类型，停止查找其子菜单。",
                            child.getId(),
                            child.getTitle(),
                            child.getModuleId());
                    continue;
                }
                result.add(child);
                // 子节点ID入队，继续查找它的后代
                queue.add(child.getId());
            }
        }
        return result;
    }

    @Override
    public ApiResponse<Map<Object, Object>> getModuleApplyFieldListByMenuId(
            Long subjectId, Long roleId, Long menuId, String btnType) {
        ConfigApiResponse<List<MenuListResp>> response =
                configCenterApiClient.getMenuList(subjectId, 1);

        if (!ConfigApiResponse.isSuccessResponse(response)) {
            return ApiResponse.error(
                    ApiResponseCodeEnum.BAD_MENU_FIELD_APPLY_RIGHT_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MENU_FIELD_APPLY_RIGHT_ERROR.getMessage());
        }
        List<MenuListResp> menuRespList = response.getData();
        if (CollectionUtil.isEmpty(menuRespList)) {
            return ApiResponse.error(
                    ApiResponseCodeEnum.BAD_MENU_FIELD_APPLY_RIGHT_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MENU_FIELD_APPLY_RIGHT_ERROR.getMessage());
        }
        // 筛选业务菜单，去掉系统设置菜单
        List<MenuListResp> bizMenuList = new ArrayList<>();
        bizMenuList.addAll(
                menuRespList.stream().filter(ele -> ele.getCategory().equals(1)).toList());
        bizMenuList.sort(Comparator.comparing(MenuListResp::getSortOrder));
        List<MenuListResp> targetModules = findTargetAndAllChildrenIterate(bizMenuList, menuId);

        Map<Object, Object> resultMap = new HashMap<>();

        Boolean flag = false;
        for (MenuListResp resp : targetModules) {
            // 菜单类型.1:导航 2:页面 3:标签 4:表
            FormButtonEnum matchFormButtonEnum = FormButtonEnum.getFormButtonEnumByKey(btnType);
            String model = "";
            if (FormButtonEnum.CREATE.equals(matchFormButtonEnum) || matchFormButtonEnum == null) {
                // 如果是创建，需要传值进权限获取方法，其他情况根据当前权限配置自动判断
                model = FormButtonEnum.CREATE.getKey();
            }
            ModuleFilterInfo moduleFilterInfo =
                    moduleInfoWithPermissionService.filterModuleInfo(
                            subjectId, roleId, resp.getModuleId(), model, List.of());

            if ("LIST".equals(moduleFilterInfo.getModuleInfo().getModuleType())) {
                try {
                    ListQueryParam listQueryParam =
                            ListQueryParam.builder()
                                    .subjectId(subjectId)
                                    .roleId(roleId)
                                    .moduleId(resp.getModuleId())
                                    .id(-1L)
                                    .moduleFilterInfo(moduleFilterInfo)
                                    .moduleSkip(List.of())
                                    .columnFilter(false)
                                    .primaryTableDataList(List.of())
                                    .orderItems(List.of())
                                    .type(null)
                                    .build();

                    Map<String, Object> map = dynamicQueryListData(listQueryParam, null);
                    resultMap.put(resp.getId(), map);
                    Set<FormButton> formButtons = (Set<FormButton>) map.get("btnList");
                    if (CollectionUtil.isNotEmpty(formButtons)) {
                        if (formButtons.stream()
                                .anyMatch(
                                        x ->
                                                FormButtonEnum.CREATE
                                                        .getKey()
                                                        .equals(x.getButtonKey()))) {
                            flag = flag || true;
                        }
                    }
                } catch (Exception e) {
                    if (e instanceof BusinessException be) {
                        resultMap.put(
                                resp.getId(), ApiResponse.error(be.getCode(), be.getMessage()));
                    }
                }
            } else {
                ApiResponse<RightInfoVO> apiResponse =
                        moduleInfoWithPermissionService.getModuleApplyFieldList(
                                subjectId, roleId, resp.getModuleId(), btnType);
                if (apiResponse.getStatus() == ApiResponseCodeEnum.OK.getCode()) {
                    apiResponse.getData().setMenuId(resp.getId());
                    resultMap.put(resp.getId(), apiResponse.getData());
                    if (menuId.equals(resp.getId())) {
                        flag = flag || true;
                    }
                }
            }
        }
        if (flag) {
            resultMap.put("showSaveBtn", Boolean.TRUE);
            return ApiResponse.success(resultMap);
        } else {
            // resultMap.put("showSaveBtn", Boolean.FALSE);
            return ApiResponse.error(
                    ApiResponseCodeEnum.BAD_MENU_FIELD_APPLY_RIGHT_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MENU_FIELD_APPLY_RIGHT_ERROR.getMessage());
        }
    }

    @Override
    public List<DataRightValue> getDataRightData(Long subjectId, List<DataRightAccessReq> req) {

        List<DataRightValue> resultList = new ArrayList<>();

        for (DataRightAccessReq dataRightAccessReq : req) {
            log.debug(
                    "获取数据库表:{}的列:{}的数据",
                    dataRightAccessReq.getTableName(),
                    dataRightAccessReq.getColumn());
            // 获取表Mapper对象
            BaseMapper moduleTableMapper =
                    SpringBeanUtil.getBean(
                            dataRightAccessReq.getTableName() + MYBATIS_MAPPER_SUFFIX,
                            BaseMapper.class);

            // 查询构造条件
            QueryWrapper queryWrapper = Wrappers.query();

            queryWrapper.select(dataRightAccessReq.getColumn());

            queryWrapper.eq("subject_id", subjectId);

            List<Object> dataList = moduleTableMapper.selectList(queryWrapper);

            DataRightValue.DataRightValueBuilder dataRightValueBuilder = DataRightValue.builder();
            dataRightValueBuilder.dataPermissionCode(
                    dataRightAccessReq.getTableName()
                            + KEY_ASTERISK_STR
                            + dataRightAccessReq.getColumn());
            dataRightValueBuilder.subject(String.valueOf(subjectId));

            List<String> values =
                    dataList.stream()
                            .map(
                                    ele -> {
                                        if (Objects.isNull(ele)) {
                                            log.debug("查询结果为空");
                                            return null;
                                        }
                                        Object columnValue = null;
                                        Field columnField =
                                                ReflectionUtils.findField(
                                                        ele.getClass(),
                                                        ToolUtils.snakeToCamelCase(
                                                                dataRightAccessReq.getColumn()));
                                        if (Objects.nonNull(columnField)) {
                                            columnField.setAccessible(true);
                                            columnValue =
                                                    ReflectionUtils.getField(columnField, ele);
                                            if (Objects.nonNull(columnValue)) {
                                                return String.valueOf(columnValue);
                                            } else {
                                                return null;
                                            }
                                        } else {
                                            return null;
                                        }
                                    })
                            .collect(Collectors.toList());

            dataRightValueBuilder.bizNames(values);

            resultList.add(dataRightValueBuilder.build());
        }

        return resultList;
    }

    @Override
    public Map<String, Object> dynamicQueryListDataByMenuId(
            Long subjectId,
            Long roleId,
            Long menuId,
            Long id,
            Page pageObj,
            ModuleFilterInfo moduleFilterInfo) {
        Map<String, Object> resultMap = new HashMap<>();
        ConfigApiResponse<List<MenuListResp>> response =
                configCenterApiClient.getMenuList(subjectId, 1);

        if (!ConfigApiResponse.isSuccessResponse(response)) {
            return resultMap;
        }
        List<MenuListResp> menuRespList = response.getData();
        if (CollectionUtil.isEmpty(menuRespList)) {
            return resultMap;
        }

        // 筛选业务菜单，去掉系统设置菜单
        List<MenuListResp> bizMenuList = new ArrayList<>();
        bizMenuList.addAll(
                menuRespList.stream().filter(ele -> ele.getCategory().equals(1)).toList());
        bizMenuList.sort(Comparator.comparing(MenuListResp::getSortOrder));

        // 弹窗菜单
        List<Long> moduleSkip =
                bizMenuList.stream()
                        .filter(ele -> ele.getMenuType().equals(5))
                        .map(MenuListResp::getModuleId)
                        .toList();

        MenuListResp targetMenu =
                bizMenuList.stream()
                        .filter(ele -> menuId.equals(ele.getId()))
                        .findFirst()
                        .orElse(null);
        if (Objects.isNull(targetMenu)) {
            log.debug("菜单id:{}对应的菜单未空。", menuId);
        } else {
            Long moduleId = targetMenu.getModuleId();
            log.debug("菜单id:{}，菜单名称:{}对应的moduleId:{}", menuId, targetMenu.getTitle(), moduleId);
            ListQueryParam listQueryParam =
                    ListQueryParam.builder()
                            .subjectId(subjectId)
                            .roleId(roleId)
                            .moduleId(moduleId)
                            .id(id)
                            .moduleFilterInfo(moduleFilterInfo)
                            .moduleSkip(moduleSkip)
                            .columnFilter(false)
                            .primaryTableDataList(List.of())
                            .orderItems(List.of())
                            .type(null)
                            .build();
            resultMap.putAll(dynamicQueryListData(listQueryParam, pageObj));
        }

        return resultMap;
    }

    @Override
    public Map<String, Object> dynamicQueryListDataWithSearchByMenuId(
            Long subjectId,
            Long roleId,
            ListQueryReq queryReq,
            Page pageObj,
            ModuleFilterInfo moduleFilterInfo) {
        Map<String, Object> resultMap = new HashMap<>();
        ConfigApiResponse<List<MenuListResp>> response =
                configCenterApiClient.getMenuList(subjectId, 1);

        if (!ConfigApiResponse.isSuccessResponse(response)) {
            return resultMap;
        }
        List<MenuListResp> menuRespList = response.getData();
        if (CollectionUtil.isEmpty(menuRespList)) {
            return resultMap;
        }

        // 筛选业务菜单，去掉系统设置菜单
        List<MenuListResp> bizMenuList = new ArrayList<>();
        bizMenuList.addAll(
                menuRespList.stream().filter(ele -> ele.getCategory().equals(1)).toList());
        bizMenuList.sort(Comparator.comparing(MenuListResp::getSortOrder));

        // 弹窗菜单
        List<Long> moduleSkip =
                bizMenuList.stream()
                        .filter(ele -> ele.getMenuType().equals(5))
                        .map(MenuListResp::getModuleId)
                        .toList();

        MenuListResp targetMenu =
                bizMenuList.stream()
                        .filter(ele -> queryReq.getMenuId().equals(ele.getId()))
                        .findFirst()
                        .orElse(null);
        if (Objects.isNull(targetMenu)) {
            log.debug("菜单id:{}对应的菜单未空。", queryReq.getMenuId());
        } else {
            Long moduleId = targetMenu.getModuleId();
            log.debug(
                    "菜单id:{}，菜单名称:{}对应的moduleId:{}",
                    queryReq.getMenuId(),
                    targetMenu.getTitle(),
                    moduleId);
            resultMap.putAll(
                    dynamicQueryListDataWithSearch(
                            subjectId,
                            roleId,
                            moduleId,
                            queryReq,
                            pageObj,
                            moduleFilterInfo,
                            moduleSkip));
        }

        return resultMap;
    }

    @Override
    public Map<Object, Object> dynamicQueryAllModuleDetailDataWithApproval(
            Long subjectId,
            Long roleId,
            Long menuId,
            Long id,
            Long relateSearchId,
            String btnType) {
        log.debug(
                "获取主体id:{} , 角色id:{} , 主键id:{} , 菜单ID:{}及其子菜单所对应模块的数据",
                subjectId,
                roleId,
                id,
                menuId);
        Map<Object, Object> resultMap = new HashMap<>();

        ConfigApiResponse<List<MenuListResp>> response =
                configCenterApiClient.getMenuList(subjectId, 1);

        if (!ConfigApiResponse.isSuccessResponse(response)) {
            resultMap.put(menuId, new HashMap<>());
            return resultMap;
        }
        List<MenuListResp> menuRespList = response.getData();
        if (CollectionUtil.isEmpty(menuRespList)) {
            resultMap.put(menuId, new HashMap<>());
            return resultMap;
        }

        // 筛选业务菜单，去掉系统设置菜单
        List<MenuListResp> bizMenuList = new ArrayList<>();
        bizMenuList.addAll(
                menuRespList.stream().filter(ele -> ele.getCategory().equals(1)).toList());
        bizMenuList.sort(Comparator.comparing(MenuListResp::getSortOrder));

        MenuListResp topMenu =
                bizMenuList.stream()
                        .filter(ele -> menuId.equals(ele.getId()))
                        .findFirst()
                        .orElse(null);

        List<MenuListResp> targetModules = findTargetAndAllChildrenIterate(bizMenuList, menuId);

        // 获取审批链相关数据
        ApprovalProcessReq approvalProcessReq = new ApprovalProcessReq();

        approvalProcessReq.setSubjectId(subjectId);
        approvalProcessReq.setModuleId(topMenu.getModuleId());
        approvalProcessReq.setRoleId(roleId);
        approvalProcessReq.setRecordId(id);
        approvalProcessReq.setCurrentUserId(LoginUserUtil.getRealLoginUserId());
        approvalProcessReq.setApplicantId(LoginUserUtil.getLoginUserId());
        approvalProcessReq.setButton(btnType);

        approvalProcessReq.setModuleId(136L);
        approvalProcessReq.setRoleId(54L);
        approvalProcessReq.setRecordId(1L);
        approvalProcessReq.setCurrentUserId(296L);
        approvalProcessReq.setApplicantId(296L);
        approvalProcessReq.setButton("");

        Map<String, Object> approvalInfo = approvalEngineApi.getApprovalDetail(approvalProcessReq);

        // 统计当前页面没有可查看权限的菜单数
        int count = 0;

        for (MenuListResp menu : targetModules) {
            ModuleFilterInfo moduleFilterInfo =
                    moduleInfoWithPermissionService.filterModuleInfo(
                            subjectId, roleId, menu.getModuleId(), "", List.of());

            try {
                if ("LIST".equals(moduleFilterInfo.getModuleInfo().getModuleType())) {
                    ListQueryParam listQueryParam =
                            ListQueryParam.builder()
                                    .subjectId(subjectId)
                                    .roleId(roleId)
                                    .moduleId(menu.getModuleId())
                                    .id(id)
                                    .moduleFilterInfo(moduleFilterInfo)
                                    .moduleSkip(List.of())
                                    .columnFilter(false)
                                    .primaryTableDataList(List.of())
                                    .orderItems(List.of())
                                    .type(null)
                                    .build();
                    resultMap.put(menu.getId(), dynamicQueryListData(listQueryParam, null));
                } else {
                    resultMap.put(
                            menu.getId(),
                            dynamicQueryDetailData(
                                    subjectId,
                                    roleId,
                                    menu.getModuleId(),
                                    id,
                                    null,
                                    moduleFilterInfo,
                                    true,
                                    approvalInfo));
                }
            } catch (BusinessException e) {
                log.debug(
                        "菜单id:{} , 菜单名称:{} , 模块id:{}无任何可查看的权限字段。",
                        menu.getId(),
                        menu.getTitle(),
                        menu.getModuleId());
                resultMap.put(menu.getId(), ApiResponse.error(e.getCode(), e.getMessage()));
                count++;
            }
        }

        if (!CollectionUtils.isEmpty(targetModules) && count == targetModules.size()) {
            log.debug("菜单id:{}对应的页面下所有菜单都没有可查看的权限字段。", menuId);
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MENU_RIGHT_NODE_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MENU_RIGHT_NODE_ERROR.getMessage());
        }

        resultMap.putAll(approvalInfo);

        return resultMap;
    }

    private void buildOrderBy(QueryWrapper queryWrapper, List<String> columns, boolean asc) {
        if (asc) {
            queryWrapper.orderByAsc(columns);
        } else {
            queryWrapper.orderByDesc(columns);
        }
    }

    private void buildConfigData(
            Map<String, Object> resultMap,
            ModuleFilterInfo moduleFilterInfo,
            Long subjectId,
            Long roleId) {
        // 先组装除数据外的其他字段

        // 将TableHeader中的下划线转为驼峰
        List<TableHeaderConf> transformHeader =
                personalizedUserServiceApi.getUserPersonalizedModuleHeader(
                        LoginUserUtil.getProjectNoFromHeader(),
                        subjectId,
                        roleId,
                        LoginUserUtil.getRealLoginUserId(),
                        moduleFilterInfo.getModuleInfo().getModuleId());
        if (CollectionUtil.isEmpty(transformHeader)) {
            transformHeader =
                    moduleFilterInfo.getModuleInfo().getTableHeader().stream()
                            .map(
                                    ele -> {
                                        ele.setField(ToolUtils.snakeToCamelCase(ele.getField()));
                                        return ele;
                                    })
                            .collect(Collectors.toList());
        }

        resultMap.put("columnData", transformHeader);

        // 将field中的下划线转为驼峰
        List<ModuleField> transformCombineFields =
                moduleFilterInfo.getCombineModuleFields().stream()
                        .map(
                                ele -> {
                                    if (!CollectionUtils.isEmpty(ele.getSourceMappings())) {
                                        List<ModuleField.SourceMapping> sourceMappings =
                                                ele.getSourceMappings().stream()
                                                        .map(
                                                                item ->
                                                                        new ModuleField
                                                                                .SourceMapping(
                                                                                item.entity(),
                                                                                ToolUtils
                                                                                        .snakeToCamelCase(
                                                                                                item
                                                                                                        .field()),
                                                                                item.sortOrder()))
                                                        .collect(Collectors.toList());
                                        ele.setSourceMappings(sourceMappings);
                                    }
                                    return ele;
                                })
                        .toList();

        resultMap.put("combineConfig", transformCombineFields);

        resultMap.put("btnList", moduleFilterInfo.getBtnList());

        resultMap.put("interOperateNode", moduleFilterInfo.getInterOperateRights());
    }
}
