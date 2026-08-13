package com.jdec.platform.common_business.biz.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.jdec.platform.approval.api.req.ApprovalProcessReq;
import com.jdec.platform.approval.api.req.SaveBusinessIdReq;
import com.jdec.platform.common_business.api.DynamicDataSaveServiceApi;
import com.jdec.platform.common_business.api.dto.common.FieldChainValueDTO;
import com.jdec.platform.common_business.api.dto.common.ModuleField;
import com.jdec.platform.common_business.api.dto.common.ModuleFilterInfo;
import com.jdec.platform.common_business.api.dto.common.ModuleTable;
import com.jdec.platform.common_business.api.dto.request.ApprovalOperationReq;
import com.jdec.platform.common_business.api.dto.request.MenuSaveReq;
import com.jdec.platform.common_business.api.dto.request.ModuleSaveReq;
import com.jdec.platform.common_business.api.dto.response.MenuListResp;
import com.jdec.platform.common_business.biz.change.CallTypeEnum;
import com.jdec.platform.common_business.biz.change.DataChangeContext;
import com.jdec.platform.common_business.biz.change.ModuleDataChangeContext;
import com.jdec.platform.common_business.biz.common.CommonConstant;
import com.jdec.platform.common_business.biz.common.DataSourceConstants;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.ApprovalApiOpenFeignClient;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.ConfigCenterApiOpenFeignClient;
import com.jdec.platform.common_business.biz.context.DataUpdateContextHolder;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.common_business.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.common_business.biz.enums.ApprovalButtonType;
import com.jdec.platform.common_business.biz.enums.FormButtonEnum;
import com.jdec.platform.common_business.biz.service.ModuleInfoWithPermissionService;
import com.jdec.platform.common_business.biz.service.SystemModuleApprovalMsgService;
import com.jdec.platform.common_business.biz.service.SystemModuleDataChangeLogService;
import com.jdec.platform.common_business.biz.util.JsonUtil;
import com.jdec.platform.common_business.biz.util.LoginUserUtil;
import com.jdec.platform.common_business.biz.util.ToolUtils;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.utils.SpringBeanUtil;
import java.io.Serializable;
import java.lang.reflect.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.STUDENT_MANAGE)
public class DynamicDataSaveServiceImpl implements DynamicDataSaveServiceApi {

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

    private static final Set<String> DEFAULT_CREATE_DATE_FIELD = Set.of("createDate", "updateDate");
    private static final List<String> DEFAULT_CREATE_SUBJECT_FIELD =
            List.of("subjectId", "subjectName", "createBy", "updateBy");
    private static final Set<String> DEFAULT_EDITE_DATE_FIELD = Set.of("updateDate");

    private static final List<String> DEFAULT_EDITE_SUBJECT_FIELD = List.of("updateBy");

    private static final Set<String> DEFAULT_EDITE_UN_FILLED_FIELD =
            Set.of("createDate", "subjectId", "subjectName", "createBy");
    private static final Set<String> DEFAULT_CREATE_UN_FILLED_FIELD = Set.of();

    private static final Map<Long, String> SUBJECT_MAP = Map.of(1L, "环保", 2L, "电气", 3L, "能化");

    private static final Integer FIELD_WRITE_PERMISSION_CODE = Integer.valueOf(2);

    private final ModuleInfoWithPermissionService moduleInfoWithPermissionService;

    private final ConfigCenterApiOpenFeignClient configCenterApiClient;

    private final ApprovalApiOpenFeignClient approvalApiOpenFeignClient;

    private final SystemModuleDataChangeLogService systemModuleDataChangeLogService;

    private final SystemModuleApprovalMsgService systemModuleApprovalMsgService;

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    private Object createMultiModuleData(Long subjectId, Long roleId, List<ModuleSaveReq> reqList) {
        List<Long> moduleIdList =
                reqList.stream().map(x -> x.getModuleId()).collect(Collectors.toList());
        // 先校验权限
        Boolean operatorPermission =
                hasOperatorPermission(moduleIdList, subjectId, roleId, FormButtonEnum.CREATE);
        if (!operatorPermission) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_FIELD_APPLY_RIGHT_ERROR.getCode(),
                    String.format("[模块数据新建][%s]模块没有申请权限", moduleIdList.toString()));
        }
        // 先 插入主模块数据,默认第一行是 主模块数据
        List<Object> primaryTableDataList = createMainModuleData(subjectId, roleId, reqList.get(0));

        if (!CollectionUtils.isEmpty(primaryTableDataList)) {
            Object primaryTableData = primaryTableDataList.get(0);
            // 如果有子模块更新
            for (int i = 1; i < reqList.size(); i++) {
                try {
                    createSubModuleData(
                            subjectId,
                            roleId,
                            reqList.get(i),
                            Long.parseLong(
                                    String.valueOf(
                                            getId(primaryTableData)))); // 默认就是取主模块主表的ID字段，写入子模块的主表上
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (CollectionUtil.isNotEmpty(primaryTableDataList)
                && primaryTableDataList.get(0) != null) {
            Object result = null;
            try {
                result = getId(primaryTableDataList.get(0));
                // 根据标志位 获取跟踪数据变更记录
                if (DataUpdateContextHolder.getDataChangeFlag()) {
                    List<Object> targets = DataUpdateContextHolder.getDataUpdateContext();
                    if (CollectionUtil.isNotEmpty(targets)) {
                        ModuleDataChangeContext context = new ModuleDataChangeContext();
                        context.setSubjectId(subjectId);
                        context.setMainModuleId(reqList.get(0).getModuleId());
                        context.setMenuId(reqList.get(0).getMenuId());
                        context.setDataId((Long) result);
                        context.setRealUserId(LoginUserUtil.getRealLoginUserId());
                        context.setMockUserId(
                                !LoginUserUtil.getRealLoginUserId()
                                                .equals(LoginUserUtil.getLoginUserId())
                                        ? LoginUserUtil.getLoginUserId()
                                        : null);
                        context.setRoleId(roleId);
                        List<DataChangeContext> dataChangeContextList = new ArrayList<>();
                        for (Object object : targets) {
                            DataChangeContext dataChangeContext = (DataChangeContext) object;
                            dataChangeContextList.add(dataChangeContext);
                        }
                        context.setCallType(CallTypeEnum.CREATE);
                        context.setDataChangeContextList(dataChangeContextList);
                        DataUpdateContextHolder.setModuleDataChangeContext(context);
                    }
                }
                return result;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    /**
     * 真实业务表多模块更新，不需要判断权限
     *
     * @param subjectId
     * @param roleId
     * @param reqList
     * @return
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    private Object createMultiModuleDataToRealTable(
            Long subjectId, Long roleId, List<ModuleSaveReq> reqList) {
        // 先 插入主模块数据,默认第一行是 主模块数据
        List<Object> primaryTableDataList =
                createMainRealTableData(subjectId, roleId, reqList.get(0));

        if (!CollectionUtils.isEmpty(primaryTableDataList)) {
            Object primaryTableData = primaryTableDataList.get(0);
            // 如果有子模块更新
            for (int i = 1; i < reqList.size(); i++) {
                try {
                    createSubRealTableData(
                            subjectId,
                            roleId,
                            reqList.get(i),
                            Long.parseLong(
                                    String.valueOf(
                                            getId(primaryTableData)))); // 默认就是取主模块主表的ID字段，写入子模块的主表上
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (CollectionUtil.isNotEmpty(primaryTableDataList)
                && primaryTableDataList.get(0) != null) {
            try {
                return getId(primaryTableDataList.get(0));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    private Object createSubModuleData(
            Long subjectId, Long roleId, ModuleSaveReq req, Long parentModuleId) {
        // 获取权限过滤后的Field结果
        ModuleFilterInfo moduleFilterInfo =
                moduleInfoWithPermissionService.filterModuleInfo(
                        subjectId,
                        roleId,
                        req.getModuleId(),
                        FormButtonEnum.CREATE.getKey(),
                        List.of());
        if (Objects.isNull(moduleFilterInfo)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_FIELD_APPLY_RIGHT_ERROR.getCode(),
                    String.format("[模块数据新建][%d]子模块没有配置定义", req.getModuleId()));
        }
        if (CollectionUtil.isEmpty(req.getModuleData())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    String.format(
                            "[模块数据新建][%d]子模块数据不能为空",
                            moduleFilterInfo.getModuleInfo().getModuleId()));
        }

        if (Objects.nonNull(moduleFilterInfo.getModuleInfo().getRelateSearchField())) {
            log.debug(
                    "子模块关联主模块主表的字段名称:{},主模块的主键id:{}",
                    moduleFilterInfo.getModuleInfo().getRelateSearchField(),
                    parentModuleId);
        } else {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    String.format(
                            "[模块数据新建][%d]子模块没有配置主模块主表的关联字段",
                            moduleFilterInfo.getModuleInfo().getModuleId()));
        }

        for (Map<String, Object> moduleItem : req.getModuleData()) {
            // 放入关联键的ID，主模块的主键id
            // 补充上层模块主表的主键到关联ID字段
            moduleItem.put(
                    moduleFilterInfo.getModuleInfo().getPrimaryTable()
                            + DB_TABLE_FIELD_SEPARATOR
                            + ToolUtils.snakeToCamelCase(
                                    moduleFilterInfo.getModuleInfo().getRelateSearchField()),
                    parentModuleId);
            log.debug(
                    "补充:{},主模块的主键id:{}到表{}的提交数据",
                    moduleFilterInfo.getModuleInfo().getPrimaryTable()
                            + DB_TABLE_FIELD_SEPARATOR
                            + moduleFilterInfo.getModuleInfo().getRelateSearchField(),
                    parentModuleId,
                    moduleFilterInfo.getModuleInfo().getPrimaryTable());
        }

        // 子模块创建无需校验权限
        //        ModuleFilterInfo moduleFilterInfo =
        //                moduleInfoWithPermissionService.filterModuleInfo(
        //                        subjectId, roleId, req.getModuleId(),
        // FormButtonEnum.CREATE.getKey());
        //        if (Objects.isNull(moduleFilterInfo)
        //                || CollectionUtils.isEmpty(moduleFilterInfo.getSimpleModuleFields())) {
        //            throw new BusinessException(
        //                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getCode(),
        // "模块没有字段编辑权限");
        //        }
        //        if (CollectionUtil.isEmpty(req.getModuleData())) {
        //            throw new BusinessException(
        //                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(), "模块数据不能为空");
        //        }

        List<Object> resultList = new ArrayList<>();

        for (Map<String, Object> moduleItem : req.getModuleData()) {
            Map<String, Map<String, Object>> tableDataMap = getTableMap(moduleItem);
            if (CollectionUtil.isEmpty(tableDataMap)) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                        String.format(
                                "[模块数据新建][%d]子模块数据不能为空",
                                moduleFilterInfo.getModuleInfo().getModuleId()));
            }
            String primaryTable = moduleFilterInfo.getModuleInfo().getPrimaryTable();
            Map<String, Object> primaryData = tableDataMap.get(primaryTable);
            if (CollectionUtil.isEmpty(primaryData)) {
                // throw new BusinessException(ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                // "[模块数据新建]子模块主表数据不能为空");
                log.warn(
                        String.format(
                                "[模块数据新建][%d]子模块主表[%s]数据[%s]能为空",
                                moduleFilterInfo.getModuleInfo().getModuleId(),
                                primaryTable,
                                tableDataMap.toString()));
                continue;
            }
            try {
                Map<String, Object> extraDataMap =
                        getCreateModuleExtraMap(
                                subjectId,
                                SUBJECT_MAP.get(subjectId),
                                LoginUserUtil.getLoginUserId(),
                                LoginUserUtil.getLoginUserId());
                Object primaryObject =
                        insertModuleTable(
                                moduleFilterInfo, primaryTable, primaryData, extraDataMap);
                // TODO 有问题，关联key不一定是主键
                Object primaryObjectId = getId(primaryObject);

                // 关联表数据
                List<ModuleTable> moduleTables = moduleFilterInfo.getModuleTableList();
                if (!CollectionUtils.isEmpty(moduleTables)) {
                    for (ModuleTable table : moduleTables) {
                        if (CommonConstant.CONFIG_MODULE_TABLE_READ_ONLY.equals(
                                table.getReadOnly())) {
                            log.warn(
                                    String.format(
                                            "[模块数据新建][%d]模块子表[%s]数据只读",
                                            moduleFilterInfo.getModuleInfo().getModuleId(),
                                            table.getTableName()));
                            continue;
                        }
                        // 关联关系
                        String relationType = table.getRelationType();
                        String joinLeftField = ToolUtils.snakeToCamelCase(table.getJoinLeftField());
                        String joinRightField =
                                ToolUtils.snakeToCamelCase(table.getJoinRightField());
                        Map<String, Object> data = tableDataMap.get(table.getTableName());

                        if (CollectionUtil.isEmpty(data)) {
                            log.warn(
                                    String.format(
                                            "[模块数据新建][%d]模块子表[%s]数据为空",
                                            moduleFilterInfo.getModuleInfo().getModuleId(),
                                            table.getTableName()));
                            continue;
                        }

                        if (TABLE_JOIN_RELATION_TYPE_ONE2ONE.equals(relationType)
                                || TABLE_JOIN_RELATION_TYPE_MULTI2ONE.equals(relationType)) {
                            Map<String, Object> mapData = (Map<String, Object>) data;
                            mapData.put(joinRightField, getField(primaryObject, joinLeftField));
                            // 写入关联表的数据
                            Object o =
                                    insertModuleTable(
                                            moduleFilterInfo,
                                            table.getTableName(),
                                            tableDataMap.get(table.getTableName()),
                                            extraDataMap);

                            //                            // 当前的子表数据中 没有ID字段
                            //                            if
                            // (!isDataHasTableId(table.getTableName(), mapData)) {
                            //                                // 写入与主表的 关联字段
                            //                                mapData.put(joinRightField,
                            // getField(primaryObject, joinLeftField));
                            //                            }
                            //
                            //                            if
                            // (!isDataHasTableId(table.getTableName(), mapData)) {
                            //                                // 走 insert
                            //                                //
                            //                                insertModuleTable(
                            //                                        table.getTableName(),
                            //
                            // tableDataMap.get(table.getTableName()),
                            //                                        extraDataMap);
                            //
                            //                            } else {
                            //                                // 走update
                            //                                updateModuleTable(
                            //                                        table.getTableName(),
                            //
                            // tableDataMap.get(table.getTableName()),
                            //                                        getUpdateModuleExtraMap());
                            //                            }
                        } else if (TABLE_JOIN_RELATION_TYPE_ONE2MULTI.equals(relationType)) {
                            // data = (List) tableDataMap.get(table.getTableName());
                        }
                    }
                }
                resultList.add(primaryObject);
            } catch (Exception e) {
                throw new BusinessException(
                        ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                        ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                        e);
            }
        }
        return resultList;
    }

    /**
     * 创建真实业务表主模块数据
     *
     * @param subjectId
     * @param roleId
     * @param req
     * @return
     */
    private List<Object> createMainRealTableData(Long subjectId, Long roleId, ModuleSaveReq req) {

        // 获取权限过滤后的Field结果
        ModuleFilterInfo moduleFilterInfo =
                moduleInfoWithPermissionService.filterModuleInfo(
                        subjectId,
                        roleId,
                        req.getModuleId(),
                        FormButtonEnum.CREATE.getKey(),
                        List.of());
        if (Objects.isNull(moduleFilterInfo)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_FIELD_APPLY_RIGHT_ERROR.getCode(),
                    String.format("[模块数据新建][%d]主模块没有配置定义", req.getModuleId()));
        }
        if (CollectionUtil.isEmpty(req.getModuleData())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    String.format(
                            "[模块数据新建][%d]主模块数据不能为空",
                            moduleFilterInfo.getModuleInfo().getModuleId()));
        }

        List<Object> resultList = new ArrayList<>();

        for (Map<String, Object> moduleItem : req.getModuleData()) {

            // 带version后缀的原始数据
            Map<String, Map<String, Object>> originTableDataMap = getTableMap(moduleItem);

            // 去除version后的数据
            Map<String, Map<String, Object>> realTableDataMap =
                    originTableDataMap.entrySet().stream()
                            .collect(
                                    Collectors.toMap(
                                            entry ->
                                                    entry.getKey().endsWith("_version")
                                                            ? entry.getKey()
                                                                    .substring(
                                                                            0,
                                                                            entry.getKey().length()
                                                                                    - "_version"
                                                                                            .length())
                                                            : entry.getKey(),
                                            Map.Entry::getValue));

            if (CollectionUtil.isEmpty(realTableDataMap)) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                        String.format(
                                "[模块数据新建][%d]主模块数据不能为空",
                                moduleFilterInfo.getModuleInfo().getModuleId()));
            }
            // 过滤掉没有权限的字段
            // tableDataMap = checkAndRemoveUnPermissionField(moduleFilterInfo, tableDataMap, true);

            String primaryTable = moduleFilterInfo.getModuleInfo().getPrimaryTable();
            String realPrimaryTable = primaryTable.replace("_version", "");
            Map<String, Object> primaryData = realTableDataMap.get(realPrimaryTable);
            if (CollectionUtil.isEmpty(primaryData)) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_FIELD_APPLY_DATA_ERROR.getCode(),
                        String.format(
                                "[模块数据新建][%d]权限过滤后模块主表[%s]数据不能为空",
                                moduleFilterInfo.getModuleInfo().getModuleId(), realPrimaryTable));
            }
            try {
                Map<String, Object> extraDataMap =
                        getCreateModuleExtraMap(
                                subjectId,
                                SUBJECT_MAP.get(subjectId),
                                LoginUserUtil.getLoginUserId(),
                                LoginUserUtil.getLoginUserId());
                Object primaryObject =
                        insertRealTable(
                                moduleFilterInfo, realPrimaryTable, primaryData, extraDataMap);
                // TODO 有问题，关联key不一定是主键
                Object primaryObjectId = getId(primaryObject);

                // 关联表数据
                List<ModuleTable> moduleTables = moduleFilterInfo.getModuleTableList();
                if (!CollectionUtils.isEmpty(moduleTables)) {
                    for (ModuleTable table : moduleTables) {
                        String joinTableName = table.getTableName().replace("_version", "");
                        // 关联关系
                        String relationType = table.getRelationType();
                        Map<String, Object> data = realTableDataMap.get(joinTableName);
                        if (CollectionUtil.isEmpty(data)) {
                            log.warn(
                                    String.format(
                                            "[模块数据新建][%d]模块子表[%s]数据为空",
                                            moduleFilterInfo.getModuleInfo().getModuleId(),
                                            joinTableName));
                            continue;
                        }

                        if (TABLE_JOIN_RELATION_TYPE_ONE2ONE.equals(relationType)
                                || TABLE_JOIN_RELATION_TYPE_MULTI2ONE.equals(relationType)) {

                            // 写入关联表的数据
                            Object o =
                                    insertRealTable(
                                            moduleFilterInfo,
                                            joinTableName,
                                            realTableDataMap.get(table.getTableName()),
                                            extraDataMap);
                        } else if (TABLE_JOIN_RELATION_TYPE_ONE2MULTI.equals(relationType)) {
                            // data = (List) tableDataMap.get(table.getTableName());
                        }
                    }
                }
                resultList.add(primaryObject);
            } catch (Exception e) {
                throw new BusinessException(
                        ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                        ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                        e);
            }
        }
        return resultList;
    }

    /**
     * 创建真实业务表子模块数据
     *
     * @param subjectId
     * @param roleId
     * @param req
     * @param relatePrimaryId
     * @return
     */
    private List<Object> createSubRealTableData(
            Long subjectId, Long roleId, ModuleSaveReq req, long relatePrimaryId) {
        // 获取权限过滤后的Field结果
        ModuleFilterInfo moduleFilterInfo =
                moduleInfoWithPermissionService.filterModuleInfo(
                        subjectId,
                        roleId,
                        req.getModuleId(),
                        FormButtonEnum.CREATE.getKey(),
                        List.of());
        if (Objects.isNull(moduleFilterInfo)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_FIELD_APPLY_RIGHT_ERROR.getCode(),
                    String.format("[模块数据新建][%d]子模块没有配置定义", req.getModuleId()));
        }
        if (CollectionUtil.isEmpty(req.getModuleData())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    String.format(
                            "[模块数据新建][%d]子模块数据不能为空",
                            moduleFilterInfo.getModuleInfo().getModuleId()));
        }

        if (Objects.nonNull(moduleFilterInfo.getModuleInfo().getRelateSearchField())) {
            log.debug(
                    "子模块关联主模块主表的字段名称:{},主模块的主键id:{}",
                    moduleFilterInfo.getModuleInfo().getRelateSearchField(),
                    relatePrimaryId);
        } else {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    String.format(
                            "[模块数据新建][%d]子模块没有配置主模块主表的关联字段",
                            moduleFilterInfo.getModuleInfo().getModuleId()));
        }

        for (Map<String, Object> moduleItem : req.getModuleData()) {
            // 放入关联键的ID，主模块的主键id
            // 补充上层模块主表的主键到关联ID字段
            moduleItem.put(
                    moduleFilterInfo.getModuleInfo().getPrimaryTable()
                            + DB_TABLE_FIELD_SEPARATOR
                            + ToolUtils.snakeToCamelCase(
                                    moduleFilterInfo.getModuleInfo().getRelateSearchField()),
                    relatePrimaryId);
            log.debug(
                    "补充:{},主模块的主键id:{}到表{}的提交数据",
                    moduleFilterInfo.getModuleInfo().getPrimaryTable()
                            + DB_TABLE_FIELD_SEPARATOR
                            + moduleFilterInfo.getModuleInfo().getRelateSearchField(),
                    relatePrimaryId,
                    moduleFilterInfo.getModuleInfo().getPrimaryTable());
        }

        List<Object> resultList = new ArrayList<>();

        for (Map<String, Object> moduleItem : req.getModuleData()) {

            // 带version后缀的原始数据
            Map<String, Map<String, Object>> originTableDataMap = getTableMap(moduleItem);

            // 去除version后的数据
            Map<String, Map<String, Object>> realTableDataMap =
                    originTableDataMap.entrySet().stream()
                            .collect(
                                    Collectors.toMap(
                                            entry ->
                                                    entry.getKey().endsWith("_version")
                                                            ? entry.getKey()
                                                                    .substring(
                                                                            0,
                                                                            entry.getKey().length()
                                                                                    - "_version"
                                                                                            .length())
                                                            : entry.getKey(),
                                            Map.Entry::getValue));

            if (CollectionUtil.isEmpty(realTableDataMap)) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                        String.format(
                                "[模块数据新建][%d]子模块数据不能为空",
                                moduleFilterInfo.getModuleInfo().getModuleId()));
            }
            String primaryTable = moduleFilterInfo.getModuleInfo().getPrimaryTable();
            String primaryRealTable = primaryTable.replace("_version", "");
            Map<String, Object> primaryData = realTableDataMap.get(primaryRealTable);
            if (CollectionUtil.isEmpty(primaryData)) {
                // throw new BusinessException(ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                // "[模块数据新建]子模块主表数据不能为空");
                log.warn(
                        String.format(
                                "[模块数据新建][%d]子模块主表[%s]数据[%s]能为空",
                                moduleFilterInfo.getModuleInfo().getModuleId(),
                                primaryRealTable,
                                realTableDataMap.toString()));
                continue;
            }
            try {
                Map<String, Object> extraDataMap =
                        getCreateModuleExtraMap(
                                subjectId,
                                SUBJECT_MAP.get(subjectId),
                                LoginUserUtil.getLoginUserId(),
                                LoginUserUtil.getLoginUserId());
                Object primaryObject =
                        insertModuleTable(
                                moduleFilterInfo, primaryRealTable, primaryData, extraDataMap);
                // TODO 有问题，关联key不一定是主键
                Object primaryObjectId = getId(primaryObject);

                // 关联表数据
                List<ModuleTable> moduleTables = moduleFilterInfo.getModuleTableList();
                if (!CollectionUtils.isEmpty(moduleTables)) {
                    for (ModuleTable table : moduleTables) {
                        String realTableName = table.getTableName().replace("_version", "");
                        if (CommonConstant.CONFIG_MODULE_TABLE_READ_ONLY.equals(
                                table.getReadOnly())) {
                            log.warn(
                                    String.format(
                                            "[模块数据新建][%d]模块子表[%s]数据只读",
                                            moduleFilterInfo.getModuleInfo().getModuleId(),
                                            realTableName));
                            continue;
                        }
                        // 关联关系
                        String relationType = table.getRelationType();
                        String joinLeftField = ToolUtils.snakeToCamelCase(table.getJoinLeftField());
                        String joinRightField =
                                ToolUtils.snakeToCamelCase(table.getJoinRightField());
                        Map<String, Object> data = realTableDataMap.get(realTableName);

                        if (CollectionUtil.isEmpty(data)) {
                            log.warn(
                                    String.format(
                                            "[模块数据新建][%d]模块子表[%s]数据为空",
                                            moduleFilterInfo.getModuleInfo().getModuleId(),
                                            realTableName));
                            continue;
                        }

                        if (TABLE_JOIN_RELATION_TYPE_ONE2ONE.equals(relationType)
                                || TABLE_JOIN_RELATION_TYPE_MULTI2ONE.equals(relationType)) {
                            Map<String, Object> mapData = (Map<String, Object>) data;
                            mapData.put(joinRightField, getField(primaryObject, joinLeftField));
                            // 写入关联表的数据
                            Object o =
                                    insertRealTable(
                                            moduleFilterInfo,
                                            realTableName,
                                            realTableDataMap.get(realTableName),
                                            extraDataMap);
                        } else if (TABLE_JOIN_RELATION_TYPE_ONE2MULTI.equals(relationType)) {
                            // data = (List) tableDataMap.get(table.getTableName());
                        }
                    }
                }
                resultList.add(primaryObject);
            } catch (Exception e) {
                throw new BusinessException(
                        ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                        ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                        e);
            }
        }
        return resultList;
    }

    /**
     * 创建主 模块数据
     *
     * @param subjectId
     * @param roleId
     * @param req
     * @return
     */
    private List<Object> createMainModuleData(Long subjectId, Long roleId, ModuleSaveReq req) {
        // 获取权限过滤后的Field结果
        ModuleFilterInfo moduleFilterInfo =
                moduleInfoWithPermissionService.filterModuleInfo(
                        subjectId,
                        roleId,
                        req.getModuleId(),
                        FormButtonEnum.CREATE.getKey(),
                        List.of());
        if (Objects.isNull(moduleFilterInfo)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_FIELD_APPLY_RIGHT_ERROR.getCode(),
                    String.format("[模块数据新建][%d]主模块没有配置定义", req.getModuleId()));
        }
        if (CollectionUtil.isEmpty(req.getModuleData())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    String.format(
                            "[模块数据新建][%d]主模块数据不能为空",
                            moduleFilterInfo.getModuleInfo().getModuleId()));
        }

        List<Object> resultList = new ArrayList<>();

        for (Map<String, Object> moduleItem : req.getModuleData()) {
            Map<String, Map<String, Object>> tableDataMap = getTableMap(moduleItem);
            if (CollectionUtil.isEmpty(tableDataMap)) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                        String.format(
                                "[模块数据新建][%d]主模块数据不能为空",
                                moduleFilterInfo.getModuleInfo().getModuleId()));
            }
            // 过滤掉没有权限的字段
            // tableDataMap = checkAndRemoveUnPermissionField(moduleFilterInfo, tableDataMap, true);

            String primaryTable = moduleFilterInfo.getModuleInfo().getPrimaryTable();
            Map<String, Object> primaryData = tableDataMap.get(primaryTable);
            if (CollectionUtil.isEmpty(primaryData)) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_FIELD_APPLY_DATA_ERROR.getCode(),
                        String.format(
                                "[模块数据新建][%d]权限过滤后模块主表[%s]数据不能为空",
                                moduleFilterInfo.getModuleInfo().getModuleId(), primaryTable));
            }
            try {
                Map<String, Object> extraDataMap =
                        getCreateModuleExtraMap(
                                subjectId,
                                SUBJECT_MAP.get(subjectId),
                                LoginUserUtil.getLoginUserId(),
                                LoginUserUtil.getLoginUserId());
                Object primaryObject =
                        insertModuleTable(
                                moduleFilterInfo, primaryTable, primaryData, extraDataMap);
                // TODO 有问题，关联key不一定是主键
                Object primaryObjectId = getId(primaryObject);

                // 关联表数据
                List<ModuleTable> moduleTables = moduleFilterInfo.getModuleTableList();
                if (!CollectionUtils.isEmpty(moduleTables)) {
                    for (ModuleTable table : moduleTables) {

                        if (CommonConstant.CONFIG_MODULE_TABLE_READ_ONLY.equals(
                                table.getReadOnly())) {
                            log.warn(
                                    String.format(
                                            "[模块数据新建][%d]模块子表[%s]数据只读",
                                            moduleFilterInfo.getModuleInfo().getModuleId(),
                                            table.getTableName()));
                            continue;
                        }
                        // 关联关系
                        String relationType = table.getRelationType();
                        Map<String, Object> data = tableDataMap.get(table.getTableName());
                        if (CollectionUtil.isEmpty(data)) {
                            log.warn(
                                    String.format(
                                            "[模块数据新建][%d]模块子表[%s]数据为空",
                                            moduleFilterInfo.getModuleInfo().getModuleId(),
                                            table.getTableName()));
                            continue;
                        }

                        if (TABLE_JOIN_RELATION_TYPE_ONE2ONE.equals(relationType)
                                || TABLE_JOIN_RELATION_TYPE_MULTI2ONE.equals(relationType)) {

                            // 写入关联表的数据
                            Object o =
                                    insertModuleTable(
                                            moduleFilterInfo,
                                            table.getTableName(),
                                            tableDataMap.get(table.getTableName()),
                                            extraDataMap);
                        } else if (TABLE_JOIN_RELATION_TYPE_ONE2MULTI.equals(relationType)) {
                            // data = (List) tableDataMap.get(table.getTableName());
                        }
                    }
                }
                resultList.add(primaryObject);
            } catch (Exception e) {
                throw new BusinessException(
                        ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                        ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                        e);
            }
        }
        return resultList;
    }

    /**
     * 多模块更新真实业务表数据
     *
     * @param subjectId
     * @param roleId
     * @param reqList
     * @return
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    private Object editMultiRealTableData(
            Long subjectId, Long roleId, List<ModuleSaveReq> reqList) {
        List<Long> moduleIdList =
                reqList.stream().map(x -> x.getModuleId()).collect(Collectors.toList());

        List<Object> primaryTableDataList =
                editMainRealTableData(subjectId, roleId, reqList.get(0));

        for (int i = 1; i < reqList.size(); i++) {
            try {
                editSubRealTableData(
                        subjectId,
                        roleId,
                        reqList.get(i),
                        Long.valueOf(getId(primaryTableDataList.get(0)).toString()));
            } catch (Exception e) {
                throw new BusinessException(
                        ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                        ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                        e);
            }
        }
        if (CollectionUtil.isNotEmpty(primaryTableDataList)
                && primaryTableDataList.get(0) != null) {
            try {
                return getId(primaryTableDataList.get(0));
            } catch (Exception e) {
                throw new BusinessException(
                        ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                        ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                        e);
            }
        }
        return null;
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    private Object editMultiModuleData(Long subjectId, Long roleId, List<ModuleSaveReq> reqList) {
        List<Long> moduleIdList =
                reqList.stream().map(x -> x.getModuleId()).collect(Collectors.toList());
        // 先校验权限
        Boolean operatorPermission =
                hasOperatorPermission(moduleIdList, subjectId, roleId, FormButtonEnum.EDITE);
        //        if (!operatorPermission) {
        //            throw new BusinessException(
        //                    ApiResponseCodeEnum.BAD_MODULE_FIELD_WRITE_RIGHT_ERROR.getCode(),
        //                    String.format("[模块数据编辑][%s]模块没有权限", moduleIdList.toString()));
        //        }
        List<Object> primaryTableDataList = editMainModuleData(subjectId, roleId, reqList.get(0));

        for (int i = 1; i < reqList.size(); i++) {
            try {
                editSubModuleData(
                        subjectId,
                        roleId,
                        reqList.get(i),
                        Long.valueOf(getId(primaryTableDataList.get(0)).toString()));
            } catch (Exception e) {
                throw new BusinessException(
                        ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                        ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                        e);
            }
        }
        if (CollectionUtil.isNotEmpty(primaryTableDataList)
                && primaryTableDataList.get(0) != null) {
            Object result = null;
            try {
                result = getId(primaryTableDataList.get(0));
                // 根据标志位 获取跟踪 数据变更记录
                if (DataUpdateContextHolder.getDataChangeFlag()) {
                    List<Object> targets = DataUpdateContextHolder.getDataUpdateContext();
                    if (CollectionUtil.isNotEmpty(targets)) {
                        ModuleDataChangeContext context = new ModuleDataChangeContext();
                        context.setSubjectId(subjectId);
                        context.setMainModuleId(reqList.get(0).getModuleId());
                        context.setMenuId(reqList.get(0).getMenuId());
                        context.setDataId((Long) result);
                        context.setRealUserId(LoginUserUtil.getRealLoginUserId());
                        context.setMockUserId(
                                !LoginUserUtil.getRealLoginUserId()
                                                .equals(LoginUserUtil.getLoginUserId())
                                        ? LoginUserUtil.getLoginUserId()
                                        : null);
                        context.setRoleId(roleId);
                        List<DataChangeContext> dataChangeContextList = new ArrayList<>();
                        for (Object object : targets) {
                            DataChangeContext dataChangeContext = (DataChangeContext) object;
                            dataChangeContextList.add(dataChangeContext);
                        }
                        context.setCallType(CallTypeEnum.EDITE);
                        context.setDataChangeContextList(dataChangeContextList);
                        DataUpdateContextHolder.setModuleDataChangeContext(context);
                    }
                }
            } catch (Exception e) {
                throw new BusinessException(
                        ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                        ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                        e);
            }
            return result;
        }
        return null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public Object editMultiMenuModuleData(Long subjectId, Long roleId, List<MenuSaveReq> req) {
        List<ModuleSaveReq> moduleSaveList = new ArrayList<>();

        ConfigApiResponse<List<MenuListResp>> response =
                configCenterApiClient.getMenuList(subjectId, 1);

        if (!ConfigApiResponse.isSuccessResponse(response)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        }
        List<MenuListResp> menuRespList = response.getData();
        if (CollectionUtil.isEmpty(menuRespList)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        }

        // 筛选业务菜单，去掉系统设置菜单
        List<MenuListResp> bizMenuList = new ArrayList<>();
        bizMenuList.addAll(
                menuRespList.stream().filter(ele -> ele.getCategory().equals(1)).toList());
        bizMenuList.sort(Comparator.comparing(MenuListResp::getSortOrder));

        // 将menuId替换为moduleId
        req.stream()
                .forEach(
                        ele -> {
                            MenuListResp target =
                                    bizMenuList.stream()
                                            .filter(n -> n.getId().equals(ele.getMenuId()))
                                            .findFirst()
                                            .orElse(null);

                            if (Objects.nonNull(target)) {
                                ModuleSaveReq moduleSaveReq = new ModuleSaveReq();
                                BeanUtils.copyProperties(ele, moduleSaveReq);
                                moduleSaveReq.setModuleId(target.getModuleId());
                                moduleSaveReq.setMenuId(target.getId());
                                moduleSaveList.add(moduleSaveReq);
                            }
                        });

        // 本类方法调用，可能有事务失效问题，最终应该是用外层事务嵌套。
        Object o = null;
        try {
            DataUpdateContextHolder.openDataChangeFlag();
            o = editMultiModuleData(subjectId, roleId, moduleSaveList);
            systemModuleDataChangeLogService.saveLog(
                    DataUpdateContextHolder.getModuleDataChangeContext());
        } finally {
            DataUpdateContextHolder.clear();
        }
        return o;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public Object createMultiMenuModuleData(Long subjectId, Long roleId, List<MenuSaveReq> req) {
        List<ModuleSaveReq> moduleSaveList = new ArrayList<>();

        ConfigApiResponse<List<MenuListResp>> response =
                configCenterApiClient.getMenuList(subjectId, 1);

        if (!ConfigApiResponse.isSuccessResponse(response)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        }
        List<MenuListResp> menuRespList = response.getData();
        if (CollectionUtil.isEmpty(menuRespList)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        }
        // 筛选业务菜单，去掉系统设置菜单
        List<MenuListResp> bizMenuList = new ArrayList<>();
        bizMenuList.addAll(
                menuRespList.stream().filter(ele -> ele.getCategory().equals(1)).toList());
        bizMenuList.sort(Comparator.comparing(MenuListResp::getSortOrder));

        MenuListResp targetTopMenu =
                bizMenuList.stream()
                        .filter(ele -> req.get(0).getMenuId().equals(ele.getId()))
                        .findFirst()
                        .orElse(null);

        // 将menuId替换为moduleId
        req.stream()
                .forEach(
                        ele -> {
                            MenuListResp target =
                                    bizMenuList.stream()
                                            .filter(n -> n.getId().equals(ele.getMenuId()))
                                            .findFirst()
                                            .orElse(null);

                            if (Objects.nonNull(target)) {
                                ModuleSaveReq moduleSaveReq = new ModuleSaveReq();
                                BeanUtils.copyProperties(ele, moduleSaveReq);
                                moduleSaveReq.setModuleId(target.getModuleId());
                                moduleSaveReq.setMenuId(target.getId());
                                moduleSaveList.add(moduleSaveReq);
                            }
                        });

        // 本类方法调用，可能有事务失效问题，最终应该是用外层事务嵌套。
        Object o = null;
        try {
            DataUpdateContextHolder.openDataChangeFlag();
            o = createMultiModuleData(subjectId, roleId, moduleSaveList);
            systemModuleDataChangeLogService.saveLog(
                    DataUpdateContextHolder.getModuleDataChangeContext());
        } finally {
            DataUpdateContextHolder.clear();
        }

        return o;
    }

    /**
     * 审批链编辑时调用，每次都是新增record表的数据，所以要去掉主键ID
     *
     * @param subjectId
     * @param roleId
     * @param moduleSaveList 添加moduleId和deleted字段后的数据
     * @param moduleSaveList originModuleSaveList原始的保存数据
     * @param needProcess 是否调用process , submit创建的新纪录不调用审批链
     * @return
     */
    private Object createMultiModuleDataWithApproval(
            Long subjectId,
            Long roleId,
            List<ModuleSaveReq> moduleSaveList,
            Long moduleId,
            boolean needProcess) {
        // 本类方法调用，可能有事务失效问题，最终应该是用外层事务嵌套。
        Object primaryData = createMultiModuleData(subjectId, roleId, moduleSaveList);

        if (needProcess) {
            // 新建快照数据
            ModuleFilterInfo moduleFilterInfo =
                    moduleInfoWithPermissionService.filterModuleInfo(
                            subjectId, roleId, moduleId, FormButtonEnum.CREATE.getKey(), List.of());

            upsertSnapshot(
                    moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                    null,
                    Long.parseLong(String.valueOf(primaryData)));

            Long approvalChainTypeId =
                    getMatchedApprovalChainTypeId(moduleFilterInfo, moduleSaveList);

            // 有审批链操作时，执行审批链
            ApprovalProcessReq approvalProcessReq = new ApprovalProcessReq();
            approvalProcessReq.setSubjectId(subjectId);
            approvalProcessReq.setButton("create");
            approvalProcessReq.setModuleId(moduleId);
            approvalProcessReq.setRecordId(Long.parseLong(String.valueOf(primaryData)));
            approvalProcessReq.setRoleId(roleId);
            approvalProcessReq.setCurrentUserId(LoginUserUtil.getRealLoginUserId());
            approvalProcessReq.setApplicantId(LoginUserUtil.getLoginUserId());
            approvalProcessReq.setPrefix("CLS");
            approvalProcessReq.setMenuId(moduleSaveList.get(0).getMenuId());

            if (Objects.nonNull(approvalChainTypeId)) {
                approvalProcessReq.setApprovalTypeId(approvalChainTypeId);
            }

            ConfigApiResponse<Map<String, Object>> processResult =
                    approvalApiOpenFeignClient.process(subjectId, approvalProcessReq);

            if (ConfigApiResponse.isSuccessResponse(processResult)) {
                log.debug("调用审批链方{}方法成功。", "process");
                // 消息推送
                Map<String, Object> data = processResult.getData();
                if (data.containsKey("templateList")) {
                    try {
                        List<Map<String, Object>> templateList =
                                (List<Map<String, Object>>) data.get("templateList");
                        systemModuleApprovalMsgService.processApprovalMsg(
                                subjectId, templateList, moduleFilterInfo, moduleSaveList);
                    } catch (Exception e) {
                        log.error("获取消息模版参数异常,e=", e);
                    }
                }
            } else {
                try {
                    log.debug(
                            "调用审批链:{}方法失败 , 请求参数:{}",
                            "process",
                            JsonUtil.obj2Json(approvalProcessReq));
                } catch (JsonProcessingException e) {
                    log.debug("请求参数json序列化失败。");
                }
                // 主动抛出异常，回滚事务
                ApiResponseCodeEnum apiResponseCodeEnum =
                        ApiResponseCodeEnum.getEnumByCode(processResult.getStatus());
                if (Objects.isNull(apiResponseCodeEnum)) {
                    apiResponseCodeEnum = ApiResponseCodeEnum.APPROVAL_PROCEDURE_CHANGE;
                }
                throw new BusinessException(
                        apiResponseCodeEnum.getCode(), apiResponseCodeEnum.getMessage());
            }
        }

        return primaryData;
    }

    private Long getMatchedApprovalChainTypeId(
            ModuleFilterInfo moduleFilterInfo, List<ModuleSaveReq> moduleSaveList) {
        List<ModuleField> fieldChainConfigs =
                moduleFilterInfo.getSimpleModuleFields().stream()
                        .filter(ele -> !CollectionUtils.isEmpty(ele.getFieldChainValues()))
                        .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(fieldChainConfigs)) {
            log.debug("模块字段没有配置审批链分类");
            return null;
        } else if (fieldChainConfigs.size() > 1) {
            log.debug("模块有多于一个字段配置了审批链分类配置");
            throw new BusinessException(
                    ApiResponseCodeEnum.APPROVAL_FIELD_VALUE_CHAIN_TYPE_CONFIG_ERROR.getCode(),
                    ApiResponseCodeEnum.APPROVAL_FIELD_VALUE_CHAIN_TYPE_CONFIG_ERROR.getMessage());
        } else {
            Map<String, List<FieldChainValueDTO>> fieldChainValueGroupByFieldKey =
                    fieldChainConfigs.stream()
                            .collect(
                                    Collectors.toMap(
                                            ele ->
                                                    ele.getTableName()
                                                            + DB_TABLE_FIELD_SEPARATOR
                                                            + ToolUtils.snakeToCamelCase(
                                                                    ele.getFieldCode()),
                                            x -> x.getFieldChainValues()));
            String fieldKey = fieldChainValueGroupByFieldKey.keySet().toArray(new String[0])[0];
            ModuleSaveReq moduleData =
                    moduleSaveList.stream()
                            .filter(
                                    ele ->
                                            ele.getModuleId()
                                                    .equals(
                                                            moduleFilterInfo
                                                                    .getModuleInfo()
                                                                    .getModuleId()))
                            .findFirst()
                            .orElse(null);

            if (Objects.nonNull(moduleData)) {
                for (Map<String, Object> dataMap : moduleData.getModuleData()) {
                    if (dataMap.containsKey(fieldKey)) {
                        Object moduleValue = dataMap.get(fieldKey);
                        log.debug(
                                "字段:{}配置了审批链分类，匹配值集合为:{} , 表单数据值:{}",
                                fieldKey,
                                fieldChainValueGroupByFieldKey.get(fieldKey).stream()
                                        .map(FieldChainValueDTO::getFieldValue)
                                        .collect(Collectors.toSet()),
                                moduleValue);
                        FieldChainValueDTO matchValueConfig =
                                fieldChainValueGroupByFieldKey.get(fieldKey).stream()
                                        .filter(
                                                ele ->
                                                        ele.getFieldValue()
                                                                .equals(
                                                                        String.valueOf(
                                                                                moduleValue)))
                                        .findFirst()
                                        .orElse(null);

                        if (Objects.nonNull(matchValueConfig)) {
                            log.debug(
                                    "字段审批链分类匹配成功，表单数据值:{}，审批链分类id:{}",
                                    moduleValue,
                                    matchValueConfig.getApprovalChainTypeId());
                            return matchValueConfig.getApprovalChainTypeId();
                        }
                    }
                }
            }
        }

        return null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public Object createMultiMenuModuleDataWithApproval(
            Long subjectId, Long roleId, List<MenuSaveReq> req) {
        List<ModuleSaveReq> moduleSaveList = new ArrayList<>();

        ConfigApiResponse<List<MenuListResp>> response =
                configCenterApiClient.getMenuList(subjectId, 1);

        if (!ConfigApiResponse.isSuccessResponse(response)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        }
        List<MenuListResp> menuRespList = response.getData();
        if (CollectionUtil.isEmpty(menuRespList)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        }
        // 筛选业务菜单，去掉系统设置菜单
        List<MenuListResp> bizMenuList = new ArrayList<>();
        bizMenuList.addAll(
                menuRespList.stream().filter(ele -> ele.getCategory().equals(1)).toList());
        bizMenuList.sort(Comparator.comparing(MenuListResp::getSortOrder));

        MenuListResp targetTopMenu =
                bizMenuList.stream()
                        .filter(ele -> req.get(0).getMenuId().equals(ele.getId()))
                        .findFirst()
                        .orElse(null);

        // 将menuId替换为moduleId
        req.stream()
                .forEach(
                        ele -> {
                            MenuListResp target =
                                    bizMenuList.stream()
                                            .filter(n -> n.getId().equals(ele.getMenuId()))
                                            .findFirst()
                                            .orElse(null);

                            if (Objects.nonNull(target)) {
                                ModuleSaveReq moduleSaveReq = new ModuleSaveReq();
                                BeanUtils.copyProperties(ele, moduleSaveReq);
                                moduleSaveReq.setModuleId(target.getModuleId());
                                moduleSaveReq.setMenuId(target.getId());
                                moduleSaveList.add(moduleSaveReq);
                            }
                        });

        // 深拷贝模块表单数据
        List<ModuleSaveReq> copyModuleSaveList;
        try {
            String originJsonStr = JsonUtil.obj2Json(moduleSaveList);
            copyModuleSaveList =
                    JsonUtil.json2Obj(originJsonStr, new TypeReference<List<ModuleSaveReq>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> fillDataMap = new HashMap<>();
        fillDataMap.put("moduleId", targetTopMenu.getModuleId());
        fillDataMap.put("deleted", false);

        // 创建前先填充module_id、deleted字段值
        fillModuleData(
                copyModuleSaveList, subjectId, roleId, targetTopMenu.getModuleId(), fillDataMap);

        // 本类方法调用，可能有事务失效问题，最终应该是用外层事务嵌套。
        Object o =
                createMultiModuleDataWithApproval(
                        subjectId, roleId, copyModuleSaveList, targetTopMenu.getModuleId(), true);
        return o;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public Object editMultiMenuModuleDataWithApproval(
            Long subjectId,
            Long roleId,
            List<MenuSaveReq> req,
            ApprovalOperationReq approvalOperation) {
        List<ModuleSaveReq> moduleSaveList = new ArrayList<>();

        ConfigApiResponse<List<MenuListResp>> response =
                configCenterApiClient.getMenuList(subjectId, 1);

        if (!ConfigApiResponse.isSuccessResponse(response)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        }
        List<MenuListResp> menuRespList = response.getData();
        if (CollectionUtil.isEmpty(menuRespList)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        }
        // 筛选业务菜单，去掉系统设置菜单
        List<MenuListResp> bizMenuList = new ArrayList<>();
        bizMenuList.addAll(
                menuRespList.stream().filter(ele -> ele.getCategory().equals(1)).toList());
        bizMenuList.sort(Comparator.comparing(MenuListResp::getSortOrder));

        MenuListResp targetTopMenu =
                bizMenuList.stream()
                        .filter(ele -> req.get(0).getMenuId().equals(ele.getId()))
                        .findFirst()
                        .orElse(null);

        // 将menuId替换为moduleId
        req.stream()
                .forEach(
                        ele -> {
                            MenuListResp target =
                                    bizMenuList.stream()
                                            .filter(n -> n.getId().equals(ele.getMenuId()))
                                            .findFirst()
                                            .orElse(null);

                            if (Objects.nonNull(target)) {
                                ModuleSaveReq moduleSaveReq = new ModuleSaveReq();
                                BeanUtils.copyProperties(ele, moduleSaveReq);
                                moduleSaveReq.setModuleId(target.getModuleId());
                                moduleSaveReq.setMenuId(target.getId());
                                moduleSaveList.add(moduleSaveReq);
                            }
                        });

        // 深拷贝模块表单数据
        List<ModuleSaveReq> originalCopyModuleSaveList;
        try {
            String originJsonStr = JsonUtil.obj2Json(moduleSaveList);
            originalCopyModuleSaveList =
                    JsonUtil.json2Obj(originJsonStr, new TypeReference<List<ModuleSaveReq>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        ApprovalProcessReq approvalProcessReq = new ApprovalProcessReq();

        ApprovalButtonType buttonType =
                ApprovalButtonType.getApprovalButtonTypeByKey(approvalOperation.getBtnType());
        if (Objects.isNull(buttonType)) {
            buttonType = ApprovalButtonType.VIEW;
        }

        Long recordId = null;

        // 获取主模块信息
        ModuleFilterInfo moduleFilterInfo =
                moduleInfoWithPermissionService.filterModuleInfo(
                        subjectId,
                        roleId,
                        targetTopMenu.getModuleId(),
                        FormButtonEnum.CREATE.getKey(),
                        List.of());

        // 开启数据变更记录 追踪
        if (buttonType != ApprovalButtonType.REAL_TIME_CHANGE) {
            DataUpdateContextHolder.openDataChangeFlag();
        }
        switch (buttonType) {
            case CREATE, CHANGE -> {
                log.debug("当前提交为创建或变更，插入业务数据。");

                if (buttonType == ApprovalButtonType.CHANGE) {
                    // 获取变更前数据id
                    Long oldRecordId =
                            getDataRecordId(
                                    moduleSaveList, subjectId, roleId, targetTopMenu.getModuleId());
                    approvalProcessReq.setOldRecordId(oldRecordId);
                    log.debug("变更后提交，获取上一条已启用数据id:{}", oldRecordId);

                    // 深拷贝模块表单数据
                    List<ModuleSaveReq> copyModuleSaveList;
                    try {
                        String originJsonStr = JsonUtil.obj2Json(moduleSaveList);
                        copyModuleSaveList =
                                JsonUtil.json2Obj(
                                        originJsonStr, new TypeReference<List<ModuleSaveReq>>() {});
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }

                    // 更新启用的数据,设置标志位
                    editPreProcessDataUpdateDelete(
                            copyModuleSaveList, subjectId, roleId, targetTopMenu.getModuleId());

                    // 去除提交数据中的主键id,走新建数据逻辑
                    removeModuleData(
                            moduleSaveList,
                            subjectId,
                            roleId,
                            targetTopMenu.getModuleId(),
                            moduleFilterInfo,
                            Set.of("id"));

                    // 提交给审批链的btnType置为submit
                    approvalOperation.setBtnType(ApprovalButtonType.SUBMIT.getKey());
                }

                Map<String, Object> fillDataMap = new HashMap<>();
                fillDataMap.put("moduleId", targetTopMenu.getModuleId());
                fillDataMap.put("deleted", false);

                // 创建前先填充module_id、deleted字段值
                fillModuleData(
                        moduleSaveList,
                        subjectId,
                        roleId,
                        targetTopMenu.getModuleId(),
                        fillDataMap);

                Object primaryData =
                        createMultiModuleDataWithApproval(
                                subjectId,
                                roleId,
                                moduleSaveList,
                                targetTopMenu.getModuleId(),
                                false);
                recordId = Long.parseLong(String.valueOf(primaryData));

                upsertSnapshot(moduleFilterInfo.getModuleInfo().getPrimaryTable(), null, recordId);
            }
            case SUBMIT, REJECT, PASS -> {
                log.debug("提交表单，更新业务数据。");

                // 获取变更前数据id
                Long oldRecordId =
                        getDataRecordId(
                                moduleSaveList, subjectId, roleId, targetTopMenu.getModuleId());
                recordId = oldRecordId;

                String tableName = moduleFilterInfo.getModuleInfo().getPrimaryTable();

                BaseMapper primaryMapper =
                        SpringBeanUtil.getBean(
                                StrUtil.toCamelCase(tableName) + MYBATIS_MAPPER_SUFFIX,
                                BaseMapper.class);

                Object mainOldData = primaryMapper.selectById((Serializable) oldRecordId);

                try {
                    Object sourceIdObj = getField(mainOldData, "sourceId");
                    if (Objects.nonNull(sourceIdObj)) {
                        Long sourceId = Long.parseLong(String.valueOf(sourceIdObj));
                        if (sourceId == -1L) {
                            log.debug("该条业务数据实时保存过。recordId:{}", oldRecordId);
                            // 主表查询构造条件
                            QueryWrapper queryWrapper = Wrappers.query();
                            queryWrapper.eq("source_id", oldRecordId);
                            Object snapshotData = primaryMapper.selectOne(queryWrapper);

                            if (Objects.nonNull(snapshotData)) {
                                log.debug("recordId:{}对应的快照数据不为空，保存旧数据到快照记录", oldRecordId);
                                approvalProcessReq.setOldRecordId(recordId);
                            } else {
                                log.debug("recordId:{}对应的快照数据为空，新建快照记录", oldRecordId);
                            }

                        } else {
                            log.debug("该条业务数据未实时保存，正常提交。recordId:{}", oldRecordId);

                            // 复制老数据更新到快照
                            upsertSnapshot(tableName, mainOldData, recordId);
                        }
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }

                Map<String, Object> fillDataMap = new HashMap<>();
                fillDataMap.put("sourceId", 0L);
                fillModuleData(
                        moduleSaveList,
                        subjectId,
                        roleId,
                        targetTopMenu.getModuleId(),
                        fillDataMap);

                // 更新主数据
                editMultiModuleData(subjectId, roleId, moduleSaveList);
            }
                // 实时保存
            case REAL_TIME_CHANGE -> {
                log.debug("实时保存，更新当前数据。");

                recordId =
                        getDataRecordId(
                                moduleSaveList, subjectId, roleId, targetTopMenu.getModuleId());

                String tableName =
                        StrUtil.toCamelCase(moduleFilterInfo.getModuleInfo().getPrimaryTable());

                BaseMapper primaryMapper =
                        SpringBeanUtil.getBean(tableName + MYBATIS_MAPPER_SUFFIX, BaseMapper.class);

                Object mainOldData = primaryMapper.selectById((Serializable) recordId);

                Field sourceIdField = ReflectionUtils.findField(mainOldData.getClass(), "sourceId");
                sourceIdField.setAccessible(true);

                Long sourceId =
                        Long.parseLong(
                                String.valueOf(
                                        ReflectionUtils.getField(sourceIdField, mainOldData)));

                if (Objects.isNull(sourceId) || sourceId != -1L) {
                    log.debug("recordId:{}当前第一次实时保存，拷贝老数据到快照", recordId);

                    upsertSnapshot(tableName, mainOldData, recordId);
                }

                Map<String, Object> fillDataMap = new HashMap<>();
                fillDataMap.put("sourceId", -1L);
                fillModuleData(
                        moduleSaveList,
                        subjectId,
                        roleId,
                        targetTopMenu.getModuleId(),
                        fillDataMap);

                // 保存当前recordId对应的数据
                editMultiModuleData(subjectId, roleId, moduleSaveList);
            }
            case CANCEL -> {
                log.debug("当前提交为取消，修改业务数据删除标识。");
                recordId =
                        editPreProcessDataUpdateDelete(
                                moduleSaveList, subjectId, roleId, targetTopMenu.getModuleId());
            }
            default -> {
                recordId =
                        getDataRecordId(
                                moduleSaveList, subjectId, roleId, targetTopMenu.getModuleId());
                log.debug("当前提交不是创建或变更，只执行审批流程。");
            }
        }

        // 非实时保存，调用审批链process
        if (buttonType != ApprovalButtonType.REAL_TIME_CHANGE) {

            ConfigApiResponse<Map<String, Object>> processResult = null;
            approvalProcessReq.setSubjectId(subjectId);
            approvalProcessReq.setModuleId(targetTopMenu.getModuleId());
            approvalProcessReq.setRoleId(roleId);
            approvalProcessReq.setRecordId(recordId);
            approvalProcessReq.setMenuId(moduleSaveList.get(0).getMenuId());

            // oldRecordId没有设置过
            if (Objects.isNull(approvalProcessReq.getOldRecordId())) {
                approvalProcessReq.setOldRecordId(recordId);
            }

            approvalProcessReq.setCurrentUserId(LoginUserUtil.getRealLoginUserId());
            approvalProcessReq.setApplicantId(LoginUserUtil.getLoginUserId());
            approvalProcessReq.setButton(approvalOperation.getBtnType());
            approvalProcessReq.setReason(approvalOperation.getReason());
            approvalProcessReq.setRejectStep(approvalOperation.getRejectStep());

            Long approvalChainTypeId =
                    getMatchedApprovalChainTypeId(moduleFilterInfo, moduleSaveList);
            if (Objects.nonNull(approvalChainTypeId)) {
                approvalProcessReq.setApprovalTypeId(approvalChainTypeId);
            }

            try {
                // 获取字段变更对比日志大json
                String reason =
                        systemModuleDataChangeLogService.compareOutChangeLogJson(
                                DataUpdateContextHolder.getModuleDataChangeContext(),
                                approvalOperation.getReason());
                approvalProcessReq.setReason(reason);
                processResult = approvalApiOpenFeignClient.process(subjectId, approvalProcessReq);
            } finally {
                DataUpdateContextHolder.clear();
            }

            if (ConfigApiResponse.isSuccessResponse(processResult)) {
                Map<String, Object> processResultMap = processResult.getData();
                // 判断审批链是否完成，若完成则更新真实表数据
                if (Objects.nonNull(processResultMap.get("end"))
                        && 1 == Integer.parseInt(String.valueOf(processResultMap.get("end")))) {
                    if (Objects.nonNull(processResultMap.get("businessId"))) {
                        Long bussinessId =
                                Long.parseLong(String.valueOf(processResultMap.get("businessId")));
                        log.debug(
                                "真实业务表businessId不为空,更新businessId:{}对应的数据",
                                processResultMap.get("businessId"));

                        // 替换审批链record表的ID为要更新的真实业务表的主键ID
                        editPreProcessDataReplaceId(
                                originalCopyModuleSaveList,
                                subjectId,
                                roleId,
                                targetTopMenu.getModuleId(),
                                bussinessId);

                        editMultiRealTableData(subjectId, roleId, originalCopyModuleSaveList);
                    } else {
                        log.debug("真实业务表businessId为空,插入数据");
                        // 审批链编辑都是在record表中新建记录，所以获取参数中的recordId作为oldRecordId参数，去除id主键走新建步骤
                        removeModuleData(
                                originalCopyModuleSaveList,
                                subjectId,
                                roleId,
                                targetTopMenu.getModuleId(),
                                moduleFilterInfo,
                                Set.of("id"));

                        Object businessId =
                                createMultiModuleDataToRealTable(
                                        subjectId, roleId, originalCopyModuleSaveList);
                        SaveBusinessIdReq saveBusinessIdReq = new SaveBusinessIdReq();
                        saveBusinessIdReq.setSubjectId(subjectId);
                        saveBusinessIdReq.setModuleId(targetTopMenu.getModuleId());
                        saveBusinessIdReq.setRecordId(recordId);
                        saveBusinessIdReq.setBusinessId(Long.parseLong(String.valueOf(businessId)));
                        approvalApiOpenFeignClient.saveBusinessId(subjectId, saveBusinessIdReq);
                    }
                } else if (Objects.nonNull(processResultMap.get("versionNo"))) {
                    // 取消提交且versionNo不为初始版本1
                    if (buttonType == ApprovalButtonType.CANCEL) {
                        if (Integer.parseInt(String.valueOf(processResultMap.get("versionNo")))
                                != 1) {
                            editMultiModuleData(subjectId, roleId, moduleSaveList);
                        }
                    }
                }
                // 消息推送
                if (processResultMap.containsKey("templateList")) {
                    try {
                        List<Map<String, Object>> templateList =
                                (List<Map<String, Object>>) processResultMap.get("templateList");
                        systemModuleApprovalMsgService.processApprovalMsg(
                                subjectId, templateList, moduleFilterInfo, moduleSaveList);
                    } catch (Exception e) {
                        log.error("获取消息模版参数异常,e=", e);
                    }
                }

            } else {
                try {
                    log.debug(
                            "调用审批链:{}方法失败 , 请求参数:{}",
                            "process",
                            JsonUtil.obj2Json(approvalProcessReq));
                } catch (JsonProcessingException e) {
                    log.debug("请求参数json序列化失败。");
                }
                // 主动抛出异常，回滚事务
                ApiResponseCodeEnum apiResponseCodeEnum =
                        ApiResponseCodeEnum.getEnumByCode(processResult.getStatus());
                if (Objects.isNull(apiResponseCodeEnum)) {
                    apiResponseCodeEnum = ApiResponseCodeEnum.APPROVAL_PROCEDURE_CHANGE;
                }
                throw new BusinessException(
                        apiResponseCodeEnum.getCode(), apiResponseCodeEnum.getMessage());
            }
        }

        return recordId;
    }

    private void upsertSnapshot(String tableName, Object mainOldData, Long oldRecordId) {
        BaseMapper primaryMapper =
                SpringBeanUtil.getBean(
                        StrUtil.toCamelCase(tableName) + MYBATIS_MAPPER_SUFFIX, BaseMapper.class);

        if (Objects.isNull(mainOldData)) {
            mainOldData = primaryMapper.selectById((Serializable) oldRecordId);
        }

        QueryWrapper queryWrapper = Wrappers.query();
        queryWrapper.eq("source_id", oldRecordId);
        Object snapshotData = primaryMapper.selectOne(queryWrapper);

        boolean exist = true;

        if (Objects.isNull(snapshotData)) {
            exist = false;
            try {
                Class primaryEntity = getEntityClass(primaryMapper);
                if (primaryEntity == null) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                            "表[" + tableName + "]mapper不存在");
                }
                Constructor<?> primaryConstructor = primaryEntity.getDeclaredConstructor();
                primaryConstructor.setAccessible(true);
                snapshotData = primaryConstructor.newInstance();
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        BeanUtils.copyProperties(mainOldData, snapshotData, "id");

        Field sourceIdField = ReflectionUtils.findField(snapshotData.getClass(), "sourceId");
        sourceIdField.setAccessible(true);
        ReflectionUtils.setField(sourceIdField, snapshotData, oldRecordId);

        Field deletedField = ReflectionUtils.findField(snapshotData.getClass(), "deleted");
        deletedField.setAccessible(true);
        ReflectionUtils.setField(deletedField, snapshotData, true);

        if (exist) {
            primaryMapper.updateById(snapshotData);
        } else {
            primaryMapper.insert(snapshotData);
        }
    }

    private void insertDataToRealTable(
            Long subjectId, Long roleId, List<ModuleSaveReq> moduleSaveList) {}

    private Long getDataRecordId(
            List<ModuleSaveReq> req, Long subjectId, Long roleId, Long moduleId) {
        // 获取主模块信息
        ModuleFilterInfo moduleFilterInfo =
                moduleInfoWithPermissionService.filterModuleInfo(
                        subjectId, roleId, moduleId, FormButtonEnum.CREATE.getKey(), List.of());

        String primaryTablePrimaryKey =
                moduleFilterInfo.getModuleInfo().getPrimaryTable()
                        + DB_TABLE_FIELD_SEPARATOR
                        + DEFAULT_PRIMARY_KEY;

        Long recordId = null;

        boolean finded = false;

        for (ModuleSaveReq ele : req) {
            if (finded) {
                break;
            }
            if (!moduleId.equals(ele.getModuleId())) {
                continue;
            }
            for (Map<String, Object> item : ele.getModuleData()) {
                if (item.containsKey(primaryTablePrimaryKey)) {
                    recordId = Long.parseLong(String.valueOf(item.get(primaryTablePrimaryKey)));
                    log.debug("找到模块主表主键:{}对应的key,值为:{}", primaryTablePrimaryKey, recordId);
                    finded = true;
                    break;
                }
            }
        }
        return recordId;
    }

    private Long editPreProcessDataUpdateDelete(
            List<ModuleSaveReq> req, Long subjectId, Long roleId, Long moduleId) {
        // 获取主模块信息
        ModuleFilterInfo moduleFilterInfo =
                moduleInfoWithPermissionService.filterModuleInfo(
                        subjectId, roleId, moduleId, FormButtonEnum.CREATE.getKey(), List.of());

        String primaryTablePrimaryKey =
                moduleFilterInfo.getModuleInfo().getPrimaryTable()
                        + DB_TABLE_FIELD_SEPARATOR
                        + DEFAULT_PRIMARY_KEY;

        Long recordId = null;

        for (ModuleSaveReq ele : req) {
            for (Map<String, Object> item : ele.getModuleData()) {
                // 补齐moduleId和deleted字段
                Set<String> tableNames = new HashSet<>();
                item.keySet()
                        .forEach(
                                str -> {
                                    if (StringUtils.isNotBlank(str)) {
                                        tableNames.add(Splitter.on(".").splitToList(str).get(0));
                                    }
                                });

                Iterator<Map.Entry<String, Object>> iterator = item.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Object> nextEntry = iterator.next();
                    if (primaryTablePrimaryKey.equals(nextEntry.getKey())) {
                        recordId = Long.parseLong(String.valueOf(nextEntry.getValue()));
                        log.debug(
                                "找到模块主表主键:{}对应的key,值为:{}",
                                nextEntry.getKey(),
                                nextEntry.getValue());
                    } else {
                        // 移除除主键之外的数据
                        iterator.remove();
                    }
                }

                log.debug("模块:{}对应的数据表为:{},顶层模块ID为:{}", ele.getModuleId(), tableNames, moduleId);
                if (!CollectionUtils.isEmpty(tableNames)) {
                    for (String tableName : tableNames) {
                        log.debug("填充数据表:{}的module_id:{}和deleted:{}字段", tableName, moduleId, true);
                        item.put(tableName + DB_TABLE_FIELD_SEPARATOR + "moduleId", moduleId);
                        item.put(tableName + DB_TABLE_FIELD_SEPARATOR + "deleted", true);
                    }
                }
            }
        }
        return recordId;
    }

    /**
     * 填充模块公共数据
     *
     * @param req
     * @param subjectId
     * @param roleId
     * @param moduleId
     * @return
     */
    private void fillModuleData(
            List<ModuleSaveReq> req,
            Long subjectId,
            Long roleId,
            Long moduleId,
            Map<String, Object> dataMap) {
        for (ModuleSaveReq ele : req) {
            for (Map<String, Object> item : ele.getModuleData()) {
                Set<String> tableNames = new HashSet<>();
                item.keySet()
                        .forEach(
                                str -> {
                                    if (StringUtils.isNotBlank(str)) {
                                        tableNames.add(Splitter.on(".").splitToList(str).get(0));
                                    }
                                });

                log.debug("模块:{}对应的数据表为:{},顶层模块ID为:{}", ele.getModuleId(), tableNames, moduleId);
                if (!CollectionUtils.isEmpty(tableNames)) {
                    for (String tableName : tableNames) {
                        for (Map.Entry<String, Object> dataEntry : dataMap.entrySet()) {
                            log.debug(
                                    "填充数据表:{}的字段:{}字段值:{}",
                                    tableName,
                                    dataEntry.getKey(),
                                    dataEntry.getValue());
                            item.put(
                                    tableName + DB_TABLE_FIELD_SEPARATOR + dataEntry.getKey(),
                                    dataEntry.getValue());
                        }
                    }
                }
            }
        }
    }

    /**
     * 去除主键ID，走新建逻辑，返回上一版本recordId
     *
     * @param req
     * @param subjectId
     * @param roleId
     * @param moduleId
     * @return
     */
    private Long removeModuleData(
            List<ModuleSaveReq> req,
            Long subjectId,
            Long roleId,
            Long moduleId,
            ModuleFilterInfo moduleFilterInfo,
            Set<String> keySet) {
        // 获取主模块信息
        if (Objects.isNull(moduleFilterInfo)) {
            moduleFilterInfo =
                    moduleInfoWithPermissionService.filterModuleInfo(
                            subjectId, roleId, moduleId, FormButtonEnum.CREATE.getKey(), List.of());
        }

        String primaryTablePrimaryKey =
                moduleFilterInfo.getModuleInfo().getPrimaryTable()
                        + DB_TABLE_FIELD_SEPARATOR
                        + DEFAULT_PRIMARY_KEY;

        Long recordId = null;

        for (ModuleSaveReq ele : req) {
            for (Map<String, Object> item : ele.getModuleData()) {
                Iterator<Map.Entry<String, Object>> iterator = item.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Object> nextEntry = iterator.next();
                    if (primaryTablePrimaryKey.equals(nextEntry.getKey())) {
                        recordId = Long.parseLong(String.valueOf(nextEntry.getValue()));
                        log.debug(
                                "找到模块主表主键:{}对应的key,值为:{}",
                                nextEntry.getKey(),
                                nextEntry.getValue());
                    }

                    if (keySet.stream()
                            .anyMatch(
                                    keyStr ->
                                            nextEntry
                                                    .getKey()
                                                    .endsWith(DB_TABLE_FIELD_SEPARATOR + keyStr))) {
                        log.debug("模块数据:{}移除", nextEntry.getKey());
                        iterator.remove();
                    }
                }
            }
        }

        return recordId;
    }

    /**
     * 替换主键ID，走真实业务表更新逻辑，返回上一版本recordId
     *
     * @param req
     * @param subjectId
     * @param roleId
     * @param moduleId
     * @return
     */
    private Long editPreProcessDataReplaceId(
            List<ModuleSaveReq> req, Long subjectId, Long roleId, Long moduleId, Long businessId) {
        // 获取主模块信息
        ModuleFilterInfo moduleFilterInfo =
                moduleInfoWithPermissionService.filterModuleInfo(
                        subjectId, roleId, moduleId, FormButtonEnum.CREATE.getKey(), List.of());

        String primaryTablePrimaryKey =
                moduleFilterInfo.getModuleInfo().getPrimaryTable()
                        + DB_TABLE_FIELD_SEPARATOR
                        + DEFAULT_PRIMARY_KEY;

        Long recordId = null;

        for (ModuleSaveReq ele : req) {
            for (Map<String, Object> item : ele.getModuleData()) {
                if (item.containsKey(primaryTablePrimaryKey)) {
                    log.debug(
                            "找到模块主表主键:{}对应的key,值为:{} , 替换为业务businessId:{}",
                            primaryTablePrimaryKey,
                            item.containsKey(primaryTablePrimaryKey),
                            businessId);
                    item.put(primaryTablePrimaryKey, businessId);
                    break;
                }
            }
        }

        return recordId;
    }

    /**
     * 更新真实业务表主模块数据
     *
     * @param subjectId
     * @param roleId
     * @param req
     * @return
     */
    private List<Object> editMainRealTableData(Long subjectId, Long roleId, ModuleSaveReq req) {
        // 获取权限过滤后的Field结果
        ModuleFilterInfo moduleFilterInfo =
                moduleInfoWithPermissionService.filterModuleInfo(
                        subjectId,
                        roleId,
                        req.getModuleId(),
                        FormButtonEnum.EDITE.getKey(),
                        List.of());

        if (Objects.isNull(moduleFilterInfo)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getCode(),
                    String.format("[模块数据编辑][%d]模块没有配置定义", req.getModuleId()));
        }
        if (CollectionUtil.isEmpty(req.getModuleData())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    String.format(
                            "[模块数据编辑][%d]主模块数据不能为空",
                            moduleFilterInfo.getModuleInfo().getModuleId()));
        }

        List<Object> returnResult = new ArrayList<>();

        for (Map<String, Object> moduleData : req.getModuleData()) {

            // 带version后缀的原始数据
            Map<String, Map<String, Object>> originTableDataMap = getTableMap(moduleData);

            // 去除version后的数据
            Map<String, Map<String, Object>> realTableDataMap =
                    originTableDataMap.entrySet().stream()
                            .collect(
                                    Collectors.toMap(
                                            entry ->
                                                    entry.getKey().endsWith("_version")
                                                            ? entry.getKey()
                                                                    .substring(
                                                                            0,
                                                                            entry.getKey().length()
                                                                                    - "_version"
                                                                                            .length())
                                                            : entry.getKey(),
                                            Map.Entry::getValue));

            if (CollectionUtil.isEmpty(realTableDataMap)) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                        String.format(
                                "[模块数据编辑][%d]主模块数据不能为空",
                                moduleFilterInfo.getModuleInfo().getModuleId()));
            }
            // 校验权限
            // tableDataMap = checkAndRemoveUnPermissionField(moduleFilterInfo, tableDataMap, true);
            if (CollectionUtil.isEmpty(realTableDataMap)) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                        String.format(
                                "[模块数据编辑][%d]主模块数据不能为空",
                                moduleFilterInfo.getModuleInfo().getModuleId()));
            }
            String primaryTable = moduleFilterInfo.getModuleInfo().getPrimaryTable();
            String primaryRealTable = primaryTable.replace("_version", "");
            Map<String, Object> primaryData = realTableDataMap.get(primaryRealTable);

            try {
                if (CollectionUtil.isEmpty(primaryData)
                        && isDataOnlyHasTableId(primaryRealTable, primaryData)) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                            String.format(
                                    "[模块数据编辑][%d]主模块主表[%s]数据不能为空",
                                    moduleFilterInfo.getModuleInfo().getModuleId(),
                                    primaryRealTable));
                }

                if (CollectionUtil.isEmpty(primaryData)
                        && !isDataHasTableId(primaryRealTable, primaryData)) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                            String.format(
                                    "[模块数据编辑][%d]主模块主表[%s]数据没有ID字段值",
                                    moduleFilterInfo.getModuleInfo().getModuleId(),
                                    primaryRealTable));
                }
                Map<String, Object> extraDataMap =
                        getUpdateModuleExtraMap(LoginUserUtil.getLoginUserId());

                // 更新主表 数据
                Object primaryObject =
                        updateRealTable(
                                moduleFilterInfo, primaryRealTable, primaryData, extraDataMap);

                // TODO 有问题，关联key不一定是主键
                Object primaryObjectId = getId(primaryObject);

                // 关联表数据 更新
                List<ModuleTable> moduleTables = moduleFilterInfo.getModuleTableList();
                if (!CollectionUtil.isEmpty(moduleTables)) {
                    for (ModuleTable table : moduleTables) {
                        String joinTableName = table.getTableName().replace("_version", "");
                        if (CommonConstant.CONFIG_MODULE_TABLE_READ_ONLY.equals(
                                table.getReadOnly())) {
                            log.warn(
                                    String.format(
                                            "[模块数据编辑][%d]模块子表[%s]数据只读",
                                            moduleFilterInfo.getModuleInfo().getModuleId(),
                                            joinTableName));
                            continue;
                        }
                        // 关联关系
                        String relationType = table.getRelationType();
                        String joinLeftField = ToolUtils.snakeToCamelCase(table.getJoinLeftField());
                        String joinRightField =
                                ToolUtils.snakeToCamelCase(table.getJoinRightField());
                        Map<String, Object> data = realTableDataMap.get(joinTableName);

                        if (CollectionUtil.isEmpty(data)) {
                            log.warn(
                                    String.format(
                                            "[模块数据编辑][%d]模块子表[%s]数据为空",
                                            moduleFilterInfo.getModuleInfo().getModuleId(),
                                            joinTableName));
                            continue;
                        }
                        if (!isDataHasTableId(joinTableName, data)) {
                            throw new BusinessException(
                                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                                    String.format(
                                            "[模块数据编辑][%d]主模块子表[%s]数据没有ID字段值",
                                            moduleFilterInfo.getModuleInfo().getModuleId(),
                                            joinTableName));
                        }
                        Object jlf = getField(primaryObject, joinLeftField);
                        if (jlf == null) {
                            BaseMapper primaryMapper =
                                    SpringBeanUtil.getBean(
                                            StrUtil.toCamelCase(primaryRealTable)
                                                    + MYBATIS_MAPPER_SUFFIX,
                                            BaseMapper.class);
                            Object dataBaseData =
                                    primaryMapper.selectById((Serializable) primaryObjectId);
                            if (dataBaseData == null) {
                                throw new BusinessException(
                                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                                        String.format(
                                                "[模块数据编辑][%d]主模块主表[%s]数据[%s]数据库不存在",
                                                moduleFilterInfo.getModuleInfo().getModuleId(),
                                                primaryRealTable,
                                                primaryObjectId.toString()));
                            }
                            jlf = getField(dataBaseData, joinLeftField);
                            if (jlf == null) {
                                throw new BusinessException(
                                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                                        String.format(
                                                "[模块数据编辑][%d]主模块子表[%s]数据无法关联主表ID",
                                                moduleFilterInfo.getModuleInfo().getModuleId(),
                                                primaryRealTable));
                            }
                        }
                        Map<String, Object> mapData = (Map<String, Object>) data;
                        mapData.put(joinRightField, jlf);
                        if (TABLE_JOIN_RELATION_TYPE_ONE2ONE.equals(relationType)
                                || TABLE_JOIN_RELATION_TYPE_MULTI2ONE.equals(relationType)) {
                            updateRealTable(
                                    moduleFilterInfo,
                                    primaryRealTable,
                                    realTableDataMap.get(primaryRealTable),
                                    extraDataMap);
                        } else if (TABLE_JOIN_RELATION_TYPE_ONE2MULTI.equals(relationType)) {
                            // data = (List) tableDataMap.get(table.getTableName());
                        }
                    }
                }
                returnResult.add(primaryObject);
            } catch (Exception e) {
                throw new BusinessException(
                        ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                        ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                        e);
            }
        }
        return returnResult;
    }

    /**
     * 更新真实业务表子模块数据
     *
     * @param subjectId
     * @param roleId
     * @param req
     * @param parentModuleId
     * @return
     */
    private List<Object> editSubRealTableData(
            Long subjectId, Long roleId, ModuleSaveReq req, Long parentModuleId) {
        // 获取权限过滤后的Field结果
        ModuleFilterInfo moduleFilterInfo =
                moduleInfoWithPermissionService.filterModuleInfo(
                        subjectId,
                        roleId,
                        req.getModuleId(),
                        FormButtonEnum.EDITE.getKey(),
                        List.of());

        if (Objects.isNull(moduleFilterInfo)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_FIELD_WRITE_RIGHT_ERROR.getCode(),
                    String.format("[模块数据编辑][%d]模块没有配置定义", req.getModuleId()));
        }
        if (CollectionUtil.isEmpty(req.getModuleData())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    String.format(
                            "[模块数据编辑][%d]子模块数据不能为空",
                            moduleFilterInfo.getModuleInfo().getModuleId()));
        }

        if (Objects.nonNull(moduleFilterInfo.getModuleInfo().getRelateSearchField())) {
            log.debug(
                    "子模块{}关联主模块主表的字段名称:{},主模块的主键id:{}",
                    moduleFilterInfo.getModuleInfo().getModuleId(),
                    moduleFilterInfo.getModuleInfo().getRelateSearchField(),
                    parentModuleId);
        } else {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    String.format(
                            "[模块数据编辑][%d]子模块没有配置主模块主表的关联字段",
                            moduleFilterInfo.getModuleInfo().getModuleId()));
        }

        for (Map<String, Object> moduleItem : req.getModuleData()) {
            // 放入关联键的ID，主模块的主键id
            // 补充上层模块主表的主键到关联ID字段
            moduleItem.put(
                    moduleFilterInfo.getModuleInfo().getPrimaryTable()
                            + DB_TABLE_FIELD_SEPARATOR
                            + moduleFilterInfo.getModuleInfo().getRelateSearchField(),
                    parentModuleId);
            log.debug(
                    "补充:{},主模块的主键id:{}到表{}的提交数据",
                    moduleFilterInfo.getModuleInfo().getPrimaryTable()
                            + DB_TABLE_FIELD_SEPARATOR
                            + moduleFilterInfo.getModuleInfo().getRelateSearchField(),
                    parentModuleId,
                    moduleFilterInfo.getModuleInfo().getPrimaryTable());
        }

        List<Object> returnResult = new ArrayList<>();

        for (Map<String, Object> moduleData : req.getModuleData()) {
            Map<String, Map<String, Object>> tableDataMap = getTableMap(moduleData);
            if (CollectionUtil.isEmpty(tableDataMap)) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                        String.format(
                                "[模块数据编辑][%d]子模块数据不能为空",
                                moduleFilterInfo.getModuleInfo().getModuleId()));
            }
            String primaryTable = moduleFilterInfo.getModuleInfo().getPrimaryTable();
            Map<String, Object> primaryData = tableDataMap.get(primaryTable);

            try {
                if (CollectionUtil.isEmpty(primaryData)) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                            String.format(
                                    "[模块数据编辑][%d]子模块主表数据不能为空",
                                    moduleFilterInfo.getModuleInfo().getModuleId()));
                }
                if (isDataOnlyHasTableId(primaryTable, primaryData)) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                            String.format(
                                    "[模块数据编辑][%d]子模块主表[%s]数据不能为空",
                                    moduleFilterInfo.getModuleInfo().getModuleId(), primaryTable));
                }
                Object primaryObject;
                Map<String, Object> extraDataMap =
                        getUpdateModuleExtraMap(LoginUserUtil.getLoginUserId());
                Boolean isMainTableInsert = false;
                // 更新
                if (isDataHasTableId(primaryTable, primaryData)) {
                    primaryObject =
                            updateModuleTable(
                                    moduleFilterInfo, primaryTable, primaryData, extraDataMap);
                    isMainTableInsert = false;
                } else {
                    // 插入
                    primaryObject =
                            insertModuleTable(
                                    moduleFilterInfo,
                                    primaryTable,
                                    primaryData,
                                    getCreateModuleExtraMap(
                                            subjectId,
                                            SUBJECT_MAP.get(subjectId),
                                            LoginUserUtil.getLoginUserId(),
                                            LoginUserUtil.getLoginUserId()));
                    isMainTableInsert = true;
                }

                // TODO 有问题，关联key不一定是主键
                Object primaryObjectId = getId(primaryObject);

                // 关联表数据更新
                List<ModuleTable> moduleTables = moduleFilterInfo.getModuleTableList();
                if (!CollectionUtil.isEmpty(moduleTables)) {

                    for (ModuleTable table : moduleTables) {
                        if (CommonConstant.CONFIG_MODULE_TABLE_READ_ONLY.equals(
                                table.getReadOnly())) {
                            log.warn(
                                    String.format(
                                            "[模块数据编辑][%d]模块子表[%s]数据只读",
                                            moduleFilterInfo.getModuleInfo().getModuleId(),
                                            table.getTableName()));
                            continue;
                        }
                        // 关联关系
                        String relationType = table.getRelationType();
                        String joinLeftField = ToolUtils.snakeToCamelCase(table.getJoinLeftField());
                        String joinRightField =
                                ToolUtils.snakeToCamelCase(table.getJoinRightField());
                        Map<String, Object> data = tableDataMap.get(table.getTableName());

                        if (CollectionUtil.isEmpty(data)) {
                            log.warn(
                                    String.format(
                                            "[模块数据编辑][%d]模块子表[%s]数据为空",
                                            moduleFilterInfo.getModuleInfo().getModuleId(),
                                            table.getTableName()));
                            continue;
                        }

                        Object jlf = getField(primaryObject, joinLeftField);
                        if (jlf == null) {
                            BaseMapper primaryMapper =
                                    SpringBeanUtil.getBean(
                                            StrUtil.toCamelCase(primaryTable)
                                                    + MYBATIS_MAPPER_SUFFIX,
                                            BaseMapper.class);
                            Object dataBaseData =
                                    primaryMapper.selectById((Serializable) primaryObjectId);
                            if (dataBaseData == null) {
                                throw new BusinessException(
                                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                                        String.format(
                                                "[模块数据编辑][%d]模块主表[%s]数据[%s]数据库没有",
                                                moduleFilterInfo.getModuleInfo().getModuleId(),
                                                table.getTableName(),
                                                primaryObjectId.toString()));
                            }
                            jlf = getField(dataBaseData, joinLeftField);
                            if (jlf == null) {
                                throw new BusinessException(
                                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                                        String.format(
                                                "[模块数据编辑][%d]子模块子表[%s]数据无法关联主表ID",
                                                moduleFilterInfo.getModuleInfo().getModuleId(),
                                                table.getTableName()));
                            }
                        }

                        Object o = null;

                        if (TABLE_JOIN_RELATION_TYPE_ONE2ONE.equals(relationType)
                                || TABLE_JOIN_RELATION_TYPE_MULTI2ONE.equals(relationType)) {
                            if (isMainTableInsert) {

                                o =
                                        insertModuleTable(
                                                moduleFilterInfo,
                                                table.getTableName(),
                                                tableDataMap.get(table.getTableName()),
                                                getCreateModuleExtraMap(
                                                        subjectId, SUBJECT_MAP.get(subjectId)));

                            } else {
                                if (!isDataHasTableId(table.getTableName(), data)) {
                                    throw new BusinessException(
                                            ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                                            String.format(
                                                    "[模块数据编辑][%d]子模块子表[%s]数据没有ID字段值",
                                                    moduleFilterInfo.getModuleInfo().getModuleId(),
                                                    table.getTableName()));
                                }
                                o =
                                        updateModuleTable(
                                                moduleFilterInfo,
                                                table.getTableName(),
                                                tableDataMap.get(table.getTableName()),
                                                extraDataMap);
                            }

                        } else if (TABLE_JOIN_RELATION_TYPE_ONE2MULTI.equals(relationType)) {
                            // data = (List) tableDataMap.get(table.getTableName());
                        }
                    }
                }
                returnResult.add(primaryObject);
            } catch (Exception e) {
                throw new BusinessException(
                        ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                        ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                        e);
            }
        }
        return returnResult;
    }

    private List<Object> editMainModuleData(Long subjectId, Long roleId, ModuleSaveReq req) {
        // 获取权限过滤后的Field结果
        ModuleFilterInfo moduleFilterInfo =
                moduleInfoWithPermissionService.filterModuleInfo(
                        subjectId,
                        roleId,
                        req.getModuleId(),
                        FormButtonEnum.EDITE.getKey(),
                        List.of());

        if (Objects.isNull(moduleFilterInfo)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getCode(),
                    String.format("[模块数据编辑][%d]模块没有配置定义", req.getModuleId()));
        }
        if (CollectionUtil.isEmpty(req.getModuleData())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    String.format(
                            "[模块数据编辑][%d]主模块数据不能为空",
                            moduleFilterInfo.getModuleInfo().getModuleId()));
        }

        List<Object> returnResult = new ArrayList<>();

        for (Map<String, Object> moduleData : req.getModuleData()) {
            Map<String, Map<String, Object>> tableDataMap = getTableMap(moduleData);
            if (CollectionUtil.isEmpty(tableDataMap)) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                        String.format(
                                "[模块数据编辑][%d]主模块数据不能为空",
                                moduleFilterInfo.getModuleInfo().getModuleId()));
            }
            // 校验权限
            // tableDataMap = checkAndRemoveUnPermissionField(moduleFilterInfo, tableDataMap, true);
            if (CollectionUtil.isEmpty(tableDataMap)) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                        String.format(
                                "[模块数据编辑][%d]主模块数据不能为空",
                                moduleFilterInfo.getModuleInfo().getModuleId()));
            }
            String primaryTable = moduleFilterInfo.getModuleInfo().getPrimaryTable();
            Map<String, Object> primaryData = tableDataMap.get(primaryTable);

            try {
                if (CollectionUtil.isEmpty(primaryData)
                        && isDataOnlyHasTableId(primaryTable, primaryData)) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                            String.format(
                                    "[模块数据编辑][%d]主模块主表[%s]数据不能为空",
                                    moduleFilterInfo.getModuleInfo().getModuleId(), primaryTable));
                }

                if (CollectionUtil.isEmpty(primaryData)
                        && !isDataHasTableId(primaryTable, primaryData)) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                            String.format(
                                    "[模块数据编辑][%d]主模块主表[%s]数据没有ID字段值",
                                    moduleFilterInfo.getModuleInfo().getModuleId(), primaryTable));
                }
                Map<String, Object> extraDataMap =
                        getUpdateModuleExtraMap(LoginUserUtil.getLoginUserId());

                // 更新主表 数据
                Object primaryObject =
                        updateModuleTable(
                                moduleFilterInfo, primaryTable, primaryData, extraDataMap);

                // TODO 有问题，关联key不一定是主键
                Object primaryObjectId = getId(primaryObject);

                // 关联表数据 更新
                List<ModuleTable> moduleTables = moduleFilterInfo.getModuleTableList();
                if (!CollectionUtil.isEmpty(moduleTables)) {
                    for (ModuleTable table : moduleTables) {

                        if (CommonConstant.CONFIG_MODULE_TABLE_READ_ONLY.equals(
                                table.getReadOnly())) {
                            log.warn(
                                    String.format(
                                            "[模块数据编辑][%d]模块子表[%s]数据只读",
                                            moduleFilterInfo.getModuleInfo().getModuleId(),
                                            table.getTableName()));
                            continue;
                        }
                        // 关联关系
                        String relationType = table.getRelationType();
                        String joinLeftField = ToolUtils.snakeToCamelCase(table.getJoinLeftField());
                        String joinRightField =
                                ToolUtils.snakeToCamelCase(table.getJoinRightField());
                        Map<String, Object> data = tableDataMap.get(table.getTableName());

                        if (CollectionUtil.isEmpty(data)) {
                            log.warn(
                                    String.format(
                                            "[模块数据编辑][%d]模块子表[%s]数据为空",
                                            moduleFilterInfo.getModuleInfo().getModuleId(),
                                            table.getTableName()));
                            continue;
                        }
                        if (!isDataHasTableId(table.getTableName(), data)) {
                            throw new BusinessException(
                                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                                    String.format(
                                            "[模块数据编辑][%d]主模块子表[%s]数据没有ID字段值",
                                            moduleFilterInfo.getModuleInfo().getModuleId(),
                                            table.getTableName()));
                        }
                        Object jlf = getField(primaryObject, joinLeftField);
                        if (jlf == null) {
                            BaseMapper primaryMapper =
                                    SpringBeanUtil.getBean(
                                            StrUtil.toCamelCase(primaryTable)
                                                    + MYBATIS_MAPPER_SUFFIX,
                                            BaseMapper.class);
                            Object dataBaseData =
                                    primaryMapper.selectById((Serializable) primaryObjectId);
                            if (dataBaseData == null) {
                                throw new BusinessException(
                                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                                        String.format(
                                                "[模块数据编辑][%d]主模块主表[%s]数据[%s]数据库不存在",
                                                moduleFilterInfo.getModuleInfo().getModuleId(),
                                                primaryTable,
                                                primaryObjectId.toString()));
                            }
                            jlf = getField(dataBaseData, joinLeftField);
                            if (jlf == null) {
                                throw new BusinessException(
                                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                                        String.format(
                                                "[模块数据编辑][%d]主模块子表[%s]数据无法关联主表ID",
                                                moduleFilterInfo.getModuleInfo().getModuleId(),
                                                table.getTableName()));
                            }
                        }
                        Map<String, Object> mapData = (Map<String, Object>) data;
                        mapData.put(joinRightField, jlf);
                        if (TABLE_JOIN_RELATION_TYPE_ONE2ONE.equals(relationType)
                                || TABLE_JOIN_RELATION_TYPE_MULTI2ONE.equals(relationType)) {
                            updateModuleTable(
                                    moduleFilterInfo,
                                    table.getTableName(),
                                    tableDataMap.get(table.getTableName()),
                                    extraDataMap);
                        } else if (TABLE_JOIN_RELATION_TYPE_ONE2MULTI.equals(relationType)) {
                            // data = (List) tableDataMap.get(table.getTableName());
                        }
                    }
                }
                returnResult.add(primaryObject);
            } catch (Exception e) {
                throw new BusinessException(
                        ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                        ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                        e);
            }
        }
        return returnResult;
    }

    private List<Object> editSubModuleData(
            Long subjectId, Long roleId, ModuleSaveReq req, Long parentModuleId) {
        // 获取权限过滤后的Field结果
        ModuleFilterInfo moduleFilterInfo =
                moduleInfoWithPermissionService.filterModuleInfo(
                        subjectId,
                        roleId,
                        req.getModuleId(),
                        FormButtonEnum.EDITE.getKey(),
                        List.of());

        if (Objects.isNull(moduleFilterInfo)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_FIELD_WRITE_RIGHT_ERROR.getCode(),
                    String.format("[模块数据编辑][%d]模块没有配置定义", req.getModuleId()));
        }
        if (CollectionUtil.isEmpty(req.getModuleData())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    String.format(
                            "[模块数据编辑][%d]子模块数据不能为空",
                            moduleFilterInfo.getModuleInfo().getModuleId()));
        }

        if (Objects.nonNull(moduleFilterInfo.getModuleInfo().getRelateSearchField())) {
            log.debug(
                    "子模块{}关联主模块主表的字段名称:{},主模块的主键id:{}",
                    moduleFilterInfo.getModuleInfo().getModuleId(),
                    moduleFilterInfo.getModuleInfo().getRelateSearchField(),
                    parentModuleId);
        } else {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    String.format(
                            "[模块数据编辑][%d]子模块没有配置主模块主表的关联字段",
                            moduleFilterInfo.getModuleInfo().getModuleId()));
        }

        for (Map<String, Object> moduleItem : req.getModuleData()) {
            // 放入关联键的ID，主模块的主键id
            // 补充上层模块主表的主键到关联ID字段
            moduleItem.put(
                    moduleFilterInfo.getModuleInfo().getPrimaryTable()
                            + DB_TABLE_FIELD_SEPARATOR
                            + moduleFilterInfo.getModuleInfo().getRelateSearchField(),
                    parentModuleId);
            log.debug(
                    "补充:{},主模块的主键id:{}到表{}的提交数据",
                    moduleFilterInfo.getModuleInfo().getPrimaryTable()
                            + DB_TABLE_FIELD_SEPARATOR
                            + moduleFilterInfo.getModuleInfo().getRelateSearchField(),
                    parentModuleId,
                    moduleFilterInfo.getModuleInfo().getPrimaryTable());
        }

        List<Object> returnResult = new ArrayList<>();

        for (Map<String, Object> moduleData : req.getModuleData()) {
            Map<String, Map<String, Object>> tableDataMap = getTableMap(moduleData);
            if (CollectionUtil.isEmpty(tableDataMap)) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                        String.format(
                                "[模块数据编辑][%d]子模块数据不能为空",
                                moduleFilterInfo.getModuleInfo().getModuleId()));
            }
            String primaryTable = moduleFilterInfo.getModuleInfo().getPrimaryTable();
            Map<String, Object> primaryData = tableDataMap.get(primaryTable);

            try {
                if (CollectionUtil.isEmpty(primaryData)) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                            String.format(
                                    "[模块数据编辑][%d]子模块主表数据不能为空",
                                    moduleFilterInfo.getModuleInfo().getModuleId()));
                }
                if (isDataOnlyHasTableId(primaryTable, primaryData)) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                            String.format(
                                    "[模块数据编辑][%d]子模块主表[%s]数据不能为空",
                                    moduleFilterInfo.getModuleInfo().getModuleId(), primaryTable));
                }
                Object primaryObject;
                Map<String, Object> extraDataMap =
                        getUpdateModuleExtraMap(LoginUserUtil.getLoginUserId());
                Boolean isMainTableInsert = false;
                // 更新
                if (isDataHasTableId(primaryTable, primaryData)) {
                    primaryObject =
                            updateModuleTable(
                                    moduleFilterInfo, primaryTable, primaryData, extraDataMap);
                    isMainTableInsert = false;
                } else {
                    // 插入
                    primaryObject =
                            insertModuleTable(
                                    moduleFilterInfo,
                                    primaryTable,
                                    primaryData,
                                    getCreateModuleExtraMap(
                                            subjectId,
                                            SUBJECT_MAP.get(subjectId),
                                            LoginUserUtil.getLoginUserId(),
                                            LoginUserUtil.getLoginUserId()));
                    isMainTableInsert = true;
                }

                // TODO 有问题，关联key不一定是主键
                Object primaryObjectId = getId(primaryObject);

                // 关联表数据更新
                List<ModuleTable> moduleTables = moduleFilterInfo.getModuleTableList();
                if (!CollectionUtil.isEmpty(moduleTables)) {

                    for (ModuleTable table : moduleTables) {
                        if (CommonConstant.CONFIG_MODULE_TABLE_READ_ONLY.equals(
                                table.getReadOnly())) {
                            log.warn(
                                    String.format(
                                            "[模块数据编辑][%d]模块子表[%s]数据只读",
                                            moduleFilterInfo.getModuleInfo().getModuleId(),
                                            table.getTableName()));
                            continue;
                        }
                        // 关联关系
                        String relationType = table.getRelationType();
                        String joinLeftField = ToolUtils.snakeToCamelCase(table.getJoinLeftField());
                        String joinRightField =
                                ToolUtils.snakeToCamelCase(table.getJoinRightField());
                        Map<String, Object> data = tableDataMap.get(table.getTableName());

                        if (CollectionUtil.isEmpty(data)) {
                            log.warn(
                                    String.format(
                                            "[模块数据编辑][%d]模块子表[%s]数据为空",
                                            moduleFilterInfo.getModuleInfo().getModuleId(),
                                            table.getTableName()));
                            continue;
                        }

                        Object jlf = getField(primaryObject, joinLeftField);
                        if (jlf == null) {
                            BaseMapper primaryMapper =
                                    SpringBeanUtil.getBean(
                                            StrUtil.toCamelCase(primaryTable)
                                                    + MYBATIS_MAPPER_SUFFIX,
                                            BaseMapper.class);
                            Object dataBaseData =
                                    primaryMapper.selectById((Serializable) primaryObjectId);
                            if (dataBaseData == null) {
                                throw new BusinessException(
                                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                                        String.format(
                                                "[模块数据编辑][%d]模块主表[%s]数据[%s]数据库没有",
                                                moduleFilterInfo.getModuleInfo().getModuleId(),
                                                table.getTableName(),
                                                primaryObjectId.toString()));
                            }
                            jlf = getField(dataBaseData, joinLeftField);
                            if (jlf == null) {
                                throw new BusinessException(
                                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                                        String.format(
                                                "[模块数据编辑][%d]子模块子表[%s]数据无法关联主表ID",
                                                moduleFilterInfo.getModuleInfo().getModuleId(),
                                                table.getTableName()));
                            }
                        }

                        Object o = null;

                        if (TABLE_JOIN_RELATION_TYPE_ONE2ONE.equals(relationType)
                                || TABLE_JOIN_RELATION_TYPE_MULTI2ONE.equals(relationType)) {
                            if (isMainTableInsert) {

                                o =
                                        insertModuleTable(
                                                moduleFilterInfo,
                                                table.getTableName(),
                                                tableDataMap.get(table.getTableName()),
                                                getCreateModuleExtraMap(
                                                        subjectId, SUBJECT_MAP.get(subjectId)));

                            } else {
                                if (!isDataHasTableId(table.getTableName(), data)) {
                                    throw new BusinessException(
                                            ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                                            String.format(
                                                    "[模块数据编辑][%d]子模块子表[%s]数据没有ID字段值",
                                                    moduleFilterInfo.getModuleInfo().getModuleId(),
                                                    table.getTableName()));
                                }
                                o =
                                        updateModuleTable(
                                                moduleFilterInfo,
                                                table.getTableName(),
                                                tableDataMap.get(table.getTableName()),
                                                extraDataMap);
                            }

                        } else if (TABLE_JOIN_RELATION_TYPE_ONE2MULTI.equals(relationType)) {
                            // data = (List) tableDataMap.get(table.getTableName());
                        }
                    }
                }
                returnResult.add(primaryObject);
            } catch (Exception e) {
                throw new BusinessException(
                        ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                        ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                        e);
            }
        }
        return returnResult;
    }

    private List<Object> editModuleData(Long subjectId, Long roleId, ModuleSaveReq req) {
        // 获取权限过滤后的Field结果
        ModuleFilterInfo moduleFilterInfo =
                moduleInfoWithPermissionService.filterModuleInfo(
                        subjectId,
                        roleId,
                        req.getModuleId(),
                        FormButtonEnum.EDITE.getKey(),
                        List.of());

        // 获取创建权限
        ModuleFilterInfo moduleFilterInfoCreate =
                moduleInfoWithPermissionService.filterModuleInfo(
                        subjectId,
                        roleId,
                        req.getModuleId(),
                        FormButtonEnum.CREATE.getKey(),
                        List.of());

        if (Objects.isNull(moduleFilterInfo)
                || CollectionUtils.isEmpty(moduleFilterInfo.getAllFields())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_RIGHT_NODE_ERROR.getCode(), "模块没有字段编辑权限");
        }
        if (CollectionUtil.isEmpty(req.getModuleData())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(), "模块数据不能为空");
        }

        List<Object> returnResult = new ArrayList<>();

        for (Map<String, Object> moduleData : req.getModuleData()) {
            Map<String, Map<String, Object>> tableDataMap = getTableMap(moduleData);
            if (CollectionUtil.isEmpty(tableDataMap)) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(), "模块数据不能为空");
            }

            String primaryTable = moduleFilterInfo.getModuleInfo().getPrimaryTable();
            Map<String, Object> primaryData = tableDataMap.get(primaryTable);

            String model = "EDIT";
            try {
                // 不是附属子表更新，主表更新默认edit
                if (Objects.isNull(moduleFilterInfo.getModuleInfo().getRelateSearchField())) {
                    model = "EDIT";
                    checkFieldPermission(moduleFilterInfo, tableDataMap);
                } else {
                    // 附属子表更新，有可能是新增，要判断是否有主键id传递
                    // 编辑模式
                    if (isDataHasTableId(primaryTable, primaryData)) {
                        log.debug("当前数据编辑，主表名称{}主键id:{}", primaryTable, primaryData.get("id"));
                        model = "EDIT";
                        checkFieldPermission(moduleFilterInfo, tableDataMap);
                    } else {
                        log.debug("当前数据编辑，主表名称{}", primaryData.get("id"));
                        model = "CREAT";
                        checkFieldPermission(moduleFilterInfoCreate, tableDataMap);
                    }
                }

                if (primaryData == null) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(), "模块主表数据不能为空");
                }
                Map<String, Object> extraDataMap = getUpdateModuleExtraMap();

                Object primaryObject;
                if (model.equals("EDIT")) {
                    primaryObject =
                            updateModuleTable(
                                    moduleFilterInfo, primaryTable, primaryData, extraDataMap);
                } else {
                    primaryObject =
                            insertModuleTable(
                                    moduleFilterInfo,
                                    primaryTable,
                                    primaryData,
                                    getCreateModuleExtraMap(subjectId, SUBJECT_MAP.get(subjectId)));
                }
                // TODO 有问题，关联key不一定是主键
                Object primaryObjectId = getId(primaryObject);

                // 关联表数据
                List<ModuleTable> moduleTables = moduleFilterInfo.getModuleTableList();
                if (!CollectionUtil.isEmpty(moduleTables)) {

                    for (ModuleTable table : moduleTables) {
                        // 关联关系
                        String relationType = table.getRelationType();
                        String joinLeftField = ToolUtils.snakeToCamelCase(table.getJoinLeftField());
                        String joinRightField =
                                ToolUtils.snakeToCamelCase(table.getJoinRightField());
                        Object data = tableDataMap.get(table.getTableName());
                        if (data == null) {
                            continue;
                        }
                        Object jlf = getField(primaryObject, joinLeftField);
                        if (jlf == null) {
                            BaseMapper primaryMapper =
                                    SpringBeanUtil.getBean(
                                            StrUtil.toCamelCase(primaryTable)
                                                    + MYBATIS_MAPPER_SUFFIX,
                                            BaseMapper.class);
                            Object dataBaseData =
                                    primaryMapper.selectById((Serializable) primaryObjectId);
                            if (dataBaseData == null) {
                                throw new BusinessException(
                                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                                        "模块数据不能为空");
                            }
                            jlf = getField(dataBaseData, joinLeftField);
                            if (jlf == null) {
                                throw new BusinessException(
                                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                                        "模块数据不能为空");
                            }
                        }
                        Map<String, Object> mapData = (Map<String, Object>) data;
                        mapData.put(joinRightField, jlf);
                        if (TABLE_JOIN_RELATION_TYPE_ONE2ONE.equals(relationType)
                                || TABLE_JOIN_RELATION_TYPE_MULTI2ONE.equals(relationType)) {
                            updateModuleTable(
                                    moduleFilterInfo,
                                    table.getTableName(),
                                    tableDataMap.get(table.getTableName()),
                                    extraDataMap);
                        } else if (TABLE_JOIN_RELATION_TYPE_ONE2MULTI.equals(relationType)) {
                            data = (List) tableDataMap.get(table.getTableName());
                        }
                    }
                }
                returnResult.add(primaryObject);
            } catch (Exception e) {
                throw new BusinessException(
                        ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                        ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                        e);
            }
        }
        return returnResult;
    }

    private Class<?> getEntityClass(Object proxyInstance) {
        Class<?>[] interfaces = proxyInstance.getClass().getInterfaces();

        Class mapperClass = interfaces[0];

        // 1. 获取当前接口所实现的带有泛型信息的父接口列表
        Type[] types = mapperClass.getGenericInterfaces();
        for (Type type : types) {
            if (type instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                // 2. 判断该父接口是否为你期望的 BaseMapper
                if (parameterizedType.getRawType() == BaseMapper.class) {
                    // 3. 获取实际类型参数列表，并返回第一个泛型参数（即实体类）
                    return (Class<?>) parameterizedType.getActualTypeArguments()[0];
                }
            }
        }
        return null;
    }

    private Map<String, Map<String, Object>> getTableMap(Map<String, Object> paramMap) {
        Map<String, Map<String, Object>> tableMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : paramMap.entrySet()) {
            String key = e.getKey();
            List<String> schemKeyList = Splitter.on(".").splitToList(key);
            if (CollectionUtil.isEmpty(schemKeyList) || schemKeyList.size() != 2) {
                continue;
            }
            if (!tableMap.containsKey(schemKeyList.get(0))) {
                tableMap.putIfAbsent(schemKeyList.get(0), new LinkedHashMap<>());
            }
            tableMap.get(schemKeyList.get(0)).put(schemKeyList.get(1), e.getValue());
        }
        return tableMap;
    }

    /**
     * 审批链完成后插入真实业务表数据，不需要模块相关的判断
     *
     * @param tableName
     * @param dataMap
     * @param extraMap
     * @return
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    private Object insertRealTable(
            ModuleFilterInfo moduleFilterInfo,
            String tableName,
            Map<String, Object> dataMap,
            Map<String, Object> extraMap)
            throws NoSuchMethodException,
                    InvocationTargetException,
                    InstantiationException,
                    IllegalAccessException {
        // 主表查询Mapper对象
        BaseMapper primaryMapper =
                SpringBeanUtil.getBean(
                        StrUtil.toCamelCase(tableName) + MYBATIS_MAPPER_SUFFIX, BaseMapper.class);
        Class primaryEntity = getEntityClass(primaryMapper);
        if (primaryEntity == null) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    "表[" + tableName + "]mapper不存在");
        }
        Constructor<?> primaryConstructor = primaryEntity.getDeclaredConstructor();
        primaryConstructor.setAccessible(true);
        Object primaryInstance = primaryConstructor.newInstance();
        fillData(
                primaryInstance,
                (Map<String, Object>) dataMap,
                false,
                DEFAULT_CREATE_UN_FILLED_FIELD);

        fillData(primaryInstance, extraMap, false, DEFAULT_CREATE_UN_FILLED_FIELD);
        primaryMapper.insert(primaryInstance);
        if (DataUpdateContextHolder.getDataChangeFlag()) {
            DataUpdateContextHolder.addDataUpdateToContext(
                    DataChangeContext.builder()
                            .moduleId(moduleFilterInfo.getModuleInfo().getModuleId())
                            .moduleFilterInfo(moduleFilterInfo)
                            .tableName(tableName)
                            .before(null)
                            .after(primaryInstance)
                            .build());
        }
        return primaryInstance;
    }

    private Object insertModuleTable(
            ModuleFilterInfo moduleFilterInfo,
            String tableName,
            Map<String, Object> dataMap,
            Map<String, Object> extraMap)
            throws NoSuchMethodException,
                    InvocationTargetException,
                    InstantiationException,
                    IllegalAccessException {
        // 主表查询Mapper对象
        BaseMapper primaryMapper =
                SpringBeanUtil.getBean(
                        StrUtil.toCamelCase(tableName) + MYBATIS_MAPPER_SUFFIX, BaseMapper.class);
        Class primaryEntity = getEntityClass(primaryMapper);
        if (primaryEntity == null) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    "表[" + tableName + "]mapper不存在");
        }
        Constructor<?> primaryConstructor = primaryEntity.getDeclaredConstructor();
        primaryConstructor.setAccessible(true);
        Object primaryInstance = primaryConstructor.newInstance();
        fillData(
                primaryInstance,
                (Map<String, Object>) dataMap,
                false,
                DEFAULT_CREATE_UN_FILLED_FIELD);

        fillData(primaryInstance, extraMap, false, DEFAULT_CREATE_UN_FILLED_FIELD);
        //        fillCreateDateField(primaryInstance, DEFAULT_CREATE_DATE_FIELD);
        //        fillSubjectField(
        //                primaryInstance,
        //                DEFAULT_CREATE_SUBJECT_FIELD,
        //                subjectId,
        //                SUBJECT_MAP.get(subjectId));
        primaryMapper.insert(primaryInstance);
        if (DataUpdateContextHolder.getDataChangeFlag()) {
            DataUpdateContextHolder.addDataUpdateToContext(
                    DataChangeContext.builder()
                            .moduleId(moduleFilterInfo.getModuleInfo().getModuleId())
                            .moduleFilterInfo(moduleFilterInfo)
                            .tableName(tableName)
                            .before(null)
                            .after(primaryInstance)
                            .build());
        }
        return primaryInstance;
    }

    /**
     * 更新真实业务表数据
     *
     * @param moduleFilterInfo
     * @param tableName
     * @param dataMap
     * @param extraMap
     * @return
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    private Object updateRealTable(
            ModuleFilterInfo moduleFilterInfo,
            String tableName,
            Map<String, Object> dataMap,
            Map<String, Object> extraMap)
            throws NoSuchMethodException,
                    InvocationTargetException,
                    InstantiationException,
                    IllegalAccessException {
        // 主表查询Mapper对象
        BaseMapper primaryMapper =
                SpringBeanUtil.getBean(
                        StrUtil.toCamelCase(tableName) + MYBATIS_MAPPER_SUFFIX, BaseMapper.class);
        Class primaryEntity = getEntityClass(primaryMapper);
        if (primaryEntity == null) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    "表[" + tableName + "]mapper不存在");
        }
        Constructor<?> primaryConstructor = primaryEntity.getDeclaredConstructor();
        primaryConstructor.setAccessible(true);
        Object primaryInstance = primaryConstructor.newInstance();
        fillData(
                primaryInstance,
                (Map<String, Object>) dataMap,
                true,
                DEFAULT_EDITE_UN_FILLED_FIELD);
        fillData(primaryInstance, extraMap, false, DEFAULT_EDITE_UN_FILLED_FIELD);
        //        fillCreateDateField(primaryInstance, DEFAULT_CREATE_DATE_FIELD);
        //        fillSubjectField(
        //                primaryInstance,
        //                DEFAULT_CREATE_SUBJECT_FIELD,
        //                subjectId,
        //                SUBJECT_MAP.get(subjectId));
        if (DataUpdateContextHolder.getDataChangeFlag()) {
            Object pre = primaryMapper.selectById((Serializable) getId(primaryInstance));
            Object newInstance = primaryConstructor.newInstance();
            BeanUtil.copyProperties(pre, newInstance, CopyOptions.create().ignoreNullValue());
            BeanUtil.copyProperties(
                    primaryInstance, newInstance, CopyOptions.create().ignoreNullValue());
            DataUpdateContextHolder.addDataUpdateToContext(
                    DataChangeContext.builder()
                            .moduleId(moduleFilterInfo.getModuleInfo().getModuleId())
                            .moduleFilterInfo(moduleFilterInfo)
                            .tableName(tableName)
                            .before(pre)
                            .after(newInstance)
                            .build());
        }
        primaryMapper.updateById(primaryInstance);
        return primaryInstance;
    }

    private Object updateModuleTable(
            ModuleFilterInfo moduleFilterInfo,
            String tableName,
            Map<String, Object> dataMap,
            Map<String, Object> extraMap)
            throws NoSuchMethodException,
                    InvocationTargetException,
                    InstantiationException,
                    IllegalAccessException {
        // 主表查询Mapper对象
        BaseMapper primaryMapper =
                SpringBeanUtil.getBean(
                        StrUtil.toCamelCase(tableName) + MYBATIS_MAPPER_SUFFIX, BaseMapper.class);
        Class primaryEntity = getEntityClass(primaryMapper);
        if (primaryEntity == null) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    "表[" + tableName + "]mapper不存在");
        }
        Constructor<?> primaryConstructor = primaryEntity.getDeclaredConstructor();
        primaryConstructor.setAccessible(true);
        Object primaryInstance = primaryConstructor.newInstance();
        fillData(
                primaryInstance,
                (Map<String, Object>) dataMap,
                true,
                DEFAULT_EDITE_UN_FILLED_FIELD);
        fillData(primaryInstance, extraMap, false, DEFAULT_EDITE_UN_FILLED_FIELD);
        //        fillCreateDateField(primaryInstance, DEFAULT_CREATE_DATE_FIELD);
        //        fillSubjectField(
        //                primaryInstance,
        //                DEFAULT_CREATE_SUBJECT_FIELD,
        //                subjectId,
        //                SUBJECT_MAP.get(subjectId));
        if (DataUpdateContextHolder.getDataChangeFlag()) {
            Object pre = primaryMapper.selectById((Serializable) getId(primaryInstance));
            Object newInstance = primaryConstructor.newInstance();
            BeanUtil.copyProperties(pre, newInstance, CopyOptions.create().ignoreNullValue());
            BeanUtil.copyProperties(
                    primaryInstance, newInstance, CopyOptions.create().ignoreNullValue());
            DataUpdateContextHolder.addDataUpdateToContext(
                    DataChangeContext.builder()
                            .moduleId(moduleFilterInfo.getModuleInfo().getModuleId())
                            .moduleFilterInfo(moduleFilterInfo)
                            .tableName(tableName)
                            .before(pre)
                            .after(newInstance)
                            .build());
        }
        primaryMapper.updateById(primaryInstance);
        return primaryInstance;
    }

    private Object deleteModuleTable(
            String tableName, Map<String, Object> dataMap, Map<String, Object> extraMap)
            throws NoSuchMethodException,
                    InvocationTargetException,
                    InstantiationException,
                    IllegalAccessException {
        // 主表查询Mapper对象
        BaseMapper primaryMapper =
                SpringBeanUtil.getBean(
                        StrUtil.toCamelCase(tableName) + MYBATIS_MAPPER_SUFFIX, BaseMapper.class);
        Class primaryEntity = getEntityClass(primaryMapper);
        if (primaryEntity == null) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    "表[" + tableName + "]mapper不存在");
        }
        Constructor<?> primaryConstructor = primaryEntity.getDeclaredConstructor();
        primaryConstructor.setAccessible(true);
        Object primaryInstance = primaryConstructor.newInstance();
        fillData(
                primaryInstance,
                (Map<String, Object>) dataMap,
                true,
                DEFAULT_CREATE_UN_FILLED_FIELD);
        fillData(primaryInstance, extraMap, false, DEFAULT_CREATE_UN_FILLED_FIELD);
        //        fillCreateDateField(primaryInstance, DEFAULT_CREATE_DATE_FIELD);
        //        fillSubjectField(
        //                primaryInstance,
        //                DEFAULT_CREATE_SUBJECT_FIELD,
        //                subjectId,
        //                SUBJECT_MAP.get(subjectId));
        primaryMapper.deleteById(primaryInstance);
        return primaryInstance;
    }

    private Map<String, Object> getCreateModuleExtraMap(Object... values) {
        Map<String, Object> dataMap = new LinkedHashMap<>();
        for (String key : DEFAULT_CREATE_DATE_FIELD) {
            dataMap.put(key, LocalDateTime.now());
        }
        if (values != null) {
            // 约定 0...1 是 subject 信息, 2  ...3 是 createBy updateBy
            for (int i = 0; i < values.length && i <= 3; i++) {
                dataMap.put(DEFAULT_CREATE_SUBJECT_FIELD.get(i), values[i]);
            }
        }
        return dataMap;
    }

    private Map<String, Object> getUpdateModuleExtraMap(Object... values) {
        Map<String, Object> dataMap = new LinkedHashMap<>();
        for (String key : DEFAULT_EDITE_DATE_FIELD) {
            dataMap.put(key, LocalDateTime.now());
        }
        if (values != null) {
            // 约定 0...约定是 createBy字段
            for (int i = 0; i < values.length && i <= 0; i++) {
                dataMap.put(DEFAULT_EDITE_SUBJECT_FIELD.get(i), values[i]);
            }
        }
        return dataMap;
    }

    private Boolean isDataHasTableId(String tableName, Map<String, Object> dataMap)
            throws NoSuchMethodException,
                    InvocationTargetException,
                    InstantiationException,
                    IllegalAccessException {
        // 主表查询Mapper对象
        BaseMapper primaryMapper =
                SpringBeanUtil.getBean(
                        StrUtil.toCamelCase(tableName) + MYBATIS_MAPPER_SUFFIX, BaseMapper.class);
        Class primaryEntity = getEntityClass(primaryMapper);
        if (primaryEntity == null) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    "表[" + tableName + "]mapper不存在");
        }
        Constructor<?> primaryConstructor = primaryEntity.getDeclaredConstructor();
        primaryConstructor.setAccessible(true);
        Object primaryInstance = primaryConstructor.newInstance();
        Field[] fields = primaryInstance.getClass().getDeclaredFields();
        for (Field f : fields) {
            TableId id = f.getDeclaredAnnotation(TableId.class);
            if (id != null && containsKey(dataMap, id.value())) {
                return getValue(dataMap, id.value()) != null
                        && StringUtils.isNotEmpty(getValue(dataMap, id.value()).toString());
            }
        }
        return Boolean.FALSE;
    }

    private Boolean isDataOnlyHasTableId(String tableName, Map<String, Object> dataMap)
            throws NoSuchMethodException,
                    InvocationTargetException,
                    InstantiationException,
                    IllegalAccessException {
        // 主表查询Mapper对象
        BaseMapper primaryMapper =
                SpringBeanUtil.getBean(
                        StrUtil.toCamelCase(tableName) + MYBATIS_MAPPER_SUFFIX, BaseMapper.class);
        Class primaryEntity = getEntityClass(primaryMapper);
        if (primaryEntity == null) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    "表[" + tableName + "]mapper不存在");
        }
        Constructor<?> primaryConstructor = primaryEntity.getDeclaredConstructor();
        primaryConstructor.setAccessible(true);
        Object primaryInstance = primaryConstructor.newInstance();
        Field[] fields = primaryInstance.getClass().getDeclaredFields();
        String idKey = null;
        for (Field f : fields) {
            TableId id = f.getDeclaredAnnotation(TableId.class);
            if (id != null) {
                idKey = id.value();
                break;
            }
        }
        return idKey != null
                && dataMap.size() == 1
                && containsKey(dataMap, idKey)
                && getValue(dataMap, idKey) != null;
    }

    private void fillData(
            Object instance,
            Map<String, Object> data,
            boolean fillId,
            Set<String> unfilledFieldList)
            throws IllegalAccessException {
        Field[] fields = instance.getClass().getDeclaredFields();
        for (Field f : fields) {
            if (fillId) {
                TableId id = f.getDeclaredAnnotation(TableId.class);
                if (id != null && containsKey(data, id.value())) {
                    fillField(instance, f, getValue(data, id.value()));
                }
            }
            // 从前端传过来的表单数据中获取对应字段的值填充到数据库实体，并过滤掉不需要设置的字段
            if (f != null
                    && containsKey(data, f.getName())
                    && !unfilledFieldList.contains(f.getName())) {
                fillField(instance, f, getValue(data, f.getName()));
            }
        }
    }

    private Object getId(Object instance) throws IllegalAccessException {
        Field[] fields = instance.getClass().getDeclaredFields();
        for (Field f : fields) {
            TableId id = f.getDeclaredAnnotation(TableId.class);
            if (id != null) {
                f.setAccessible(true);
                return f.get(instance);
            }
        }
        return null;
    }

    private Object getField(Object instance, String field) throws IllegalAccessException {
        Field[] fields = instance.getClass().getDeclaredFields();
        for (Field f : fields) {
            TableId id = f.getDeclaredAnnotation(TableId.class);
            if (id != null && StringUtils.equalsIgnoreCase(id.value(), field)) {
                f.setAccessible(true);
                return f.get(instance);
            }

            if (f != null && StringUtils.equalsIgnoreCase(f.getName(), field)) {
                f.setAccessible(true);
                return f.get(instance);
            }
        }
        return null;
    }

    private void fillField(Object instance, Field f, Object parameter)
            throws IllegalAccessException {
        if (parameter == null) {
            return;
        }
        f.setAccessible(true);
        Class c = f.getType();
        if (c == String.class) {
            f.set(instance, parameter.toString());
        } else if (c == Long.class) {
            if (parameter.getClass() == String.class) {
                f.set(
                        instance,
                        StringUtils.isEmpty(parameter.toString())
                                ? null
                                : Long.valueOf(parameter.toString()));
            } else if (parameter.getClass() == Long.class) {
                f.set(instance, parameter);
            } else if (parameter.getClass() == Integer.class) {
                f.set(instance, Long.valueOf(parameter.toString()));
            }
        } else if (c == Integer.class) {
            if (parameter.getClass() == String.class) {
                f.set(
                        instance,
                        StringUtils.isEmpty(parameter.toString())
                                ? null
                                : Integer.valueOf(parameter.toString()));
            } else if (parameter.getClass() == Long.class) {
                f.set(instance, Integer.valueOf(parameter.toString()));
            } else if (parameter.getClass() == Integer.class) {
                f.set(instance, parameter);
            }
        } else if (c == Double.class) {
            if (parameter.getClass() == String.class) {
                f.set(
                        instance,
                        StringUtils.isEmpty(parameter.toString())
                                ? null
                                : Double.valueOf(parameter.toString()));
            } else if (parameter.getClass() == Long.class) {
                f.set(instance, Double.valueOf(parameter.toString()));
            } else if (parameter.getClass() == Integer.class) {
                f.set(instance, Double.valueOf(parameter.toString()));
            }
        } else if (c == LocalDateTime.class) {
            if (parameter.getClass() == String.class) {
                if (parameter.toString().indexOf("T") >= 0) {
                    f.set(
                            instance,
                            DateUtil.parseLocalDateTime(
                                    parameter.toString(), DatePattern.UTC_SIMPLE_PATTERN));
                } else {
                    f.set(
                            instance,
                            DateUtil.parseLocalDateTime(
                                    parameter.toString(), DatePattern.NORM_DATETIME_PATTERN));
                }
            } else if (parameter.getClass() == LocalDateTime.class) {
                f.set(instance, parameter);
            }
        } else if (c == Boolean.class) {
            if (parameter.getClass() == String.class) {
                f.set(instance, "true".equals(parameter));
            } else if (parameter.getClass() == Boolean.class) {
                f.set(instance, parameter);
            }
        }
    }

    private boolean containsKey(Map<String, Object> paraMap, String fieldKey) {
        if (CollectionUtil.isEmpty(paraMap)) {
            return false;
        }
        return paraMap.keySet().stream().anyMatch(x -> StringUtils.equalsIgnoreCase(x, fieldKey));
    }

    private Object getValue(Map<String, Object> paraMap, String fieldKey) {
        if (CollectionUtil.isEmpty(paraMap)) {
            return null;
        }
        Map.Entry e =
                paraMap.entrySet().stream()
                        .filter(x -> StringUtils.equalsIgnoreCase(x.getKey(), fieldKey))
                        .findFirst()
                        .orElseGet(() -> null);
        return e != null ? e.getValue() : null;
    }

    private Boolean checkFieldPermission(
            ModuleFilterInfo moduleFilterInfo, Map<String, Map<String, Object>> dataMap) {

        Map<String, Map<String, Integer>> tableFieldMap = new LinkedHashMap<>();
        for (ModuleField field : moduleFilterInfo.getSimpleModuleFields()) {
            if (!tableFieldMap.containsKey(field.getTableName())) {
                tableFieldMap.putIfAbsent(field.getTableName(), new LinkedHashMap<>());
            }
            tableFieldMap
                    .get(field.getTableName())
                    .put(field.getFieldCode(), field.getPermissionCode());
        }

        if (!tableFieldMap.containsKey(moduleFilterInfo.getModuleInfo().getPrimaryTable())) {
            tableFieldMap.putIfAbsent(
                    moduleFilterInfo.getModuleInfo().getPrimaryTable(), new LinkedHashMap<>());
        }

        if (CollectionUtil.isNotEmpty(moduleFilterInfo.getModuleTableList())) {
            for (ModuleTable moduleTable : moduleFilterInfo.getModuleTableList()) {
                // 关联字段，ID 默认有权限编辑
                tableFieldMap
                        .get(moduleFilterInfo.getModuleInfo().getPrimaryTable())
                        .put(moduleTable.getJoinLeftField(), FIELD_WRITE_PERMISSION_CODE);
                log.info(
                        "放开权限校验字段,模块={},表={},字段={}",
                        moduleFilterInfo.getModuleInfo().getModuleId(),
                        moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                        moduleTable.getJoinLeftField());
                if (!tableFieldMap.containsKey(moduleTable.getTableName())) {
                    tableFieldMap.putIfAbsent(moduleTable.getTableName(), new LinkedHashMap<>());
                }
                tableFieldMap
                        .get(moduleTable.getTableName())
                        .put(moduleTable.getJoinRightField(), FIELD_WRITE_PERMISSION_CODE);
                log.info(
                        "放开权限校验字段,模块={},表={},字段={}",
                        moduleFilterInfo.getModuleInfo().getModuleId(),
                        moduleTable.getTableName(),
                        moduleTable.getJoinRightField());

                tableFieldMap
                        .get(moduleTable.getTableName())
                        .put(DEFAULT_PRIMARY_KEY, FIELD_WRITE_PERMISSION_CODE);
                log.info(
                        "放开权限校验字段,模块={},表={},字段={}",
                        moduleFilterInfo.getModuleInfo().getModuleId(),
                        moduleTable.getTableName(),
                        DEFAULT_PRIMARY_KEY);
            }
        }
        tableFieldMap
                .get(moduleFilterInfo.getModuleInfo().getPrimaryTable())
                .put(DEFAULT_PRIMARY_KEY, FIELD_WRITE_PERMISSION_CODE);
        log.info(
                "放开权限校验字段,模块={},表={},字段={}",
                moduleFilterInfo.getModuleInfo().getModuleId(),
                moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                DEFAULT_PRIMARY_KEY);

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> tableData : dataMap.entrySet()) {
            Map<String, Integer> fieldMap = tableFieldMap.get(tableData.getKey());
            for (Map.Entry<String, Object> value : tableData.getValue().entrySet()) {
                if (!fieldMap.containsKey(value.getKey())
                        || !FIELD_WRITE_PERMISSION_CODE.equals(fieldMap.get(value.getKey()))) {
                    result.add(tableData.getKey() + DB_TABLE_FIELD_SEPARATOR + value.getKey());
                }
            }
        }
        if (CollectionUtil.isNotEmpty(result)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_FIELD_WRITE_RIGHT_ERROR.getCode(),
                    "模块字段没有编辑权限[" + Joiner.on(",").join(result) + "]");
        }
        return Boolean.TRUE;
    }

    private Map<String, Map<String, Integer>> getFieldPermission(
            ModuleFilterInfo moduleFilterInfo) {

        Map<String, Map<String, Integer>> tableFieldMap = new LinkedHashMap<>();
        for (ModuleField field : moduleFilterInfo.getSimpleModuleFields()) {
            if (!tableFieldMap.containsKey(field.getTableName())) {
                tableFieldMap.putIfAbsent(field.getTableName(), new LinkedHashMap<>());
            }
            tableFieldMap
                    .get(field.getTableName())
                    .put(field.getFieldCode(), field.getPermissionCode());
        }

        if (!tableFieldMap.containsKey(moduleFilterInfo.getModuleInfo().getPrimaryTable())) {
            tableFieldMap.putIfAbsent(
                    moduleFilterInfo.getModuleInfo().getPrimaryTable(), new LinkedHashMap<>());
        }

        if (CollectionUtil.isNotEmpty(moduleFilterInfo.getModuleTableList())) {
            for (ModuleTable moduleTable : moduleFilterInfo.getModuleTableList()) {
                // 关联字段，ID 默认有权限编辑
                tableFieldMap
                        .get(moduleFilterInfo.getModuleInfo().getPrimaryTable())
                        .put(moduleTable.getJoinLeftField(), FIELD_WRITE_PERMISSION_CODE);
                log.info(
                        "放开权限校验字段,模块={},表={},字段={}",
                        moduleFilterInfo.getModuleInfo().getModuleId(),
                        moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                        moduleTable.getJoinLeftField());
                if (!tableFieldMap.containsKey(moduleTable.getTableName())) {
                    tableFieldMap.putIfAbsent(moduleTable.getTableName(), new LinkedHashMap<>());
                }
                tableFieldMap
                        .get(moduleTable.getTableName())
                        .put(moduleTable.getJoinRightField(), FIELD_WRITE_PERMISSION_CODE);
                log.info(
                        "放开权限校验字段,模块={},表={},字段={}",
                        moduleFilterInfo.getModuleInfo().getModuleId(),
                        moduleTable.getTableName(),
                        moduleTable.getJoinRightField());

                tableFieldMap
                        .get(moduleTable.getTableName())
                        .put(DEFAULT_PRIMARY_KEY, FIELD_WRITE_PERMISSION_CODE);
                log.info(
                        "放开权限校验字段,模块={},表={},字段={}",
                        moduleFilterInfo.getModuleInfo().getModuleId(),
                        moduleTable.getTableName(),
                        DEFAULT_PRIMARY_KEY);
            }
        }
        tableFieldMap
                .get(moduleFilterInfo.getModuleInfo().getPrimaryTable())
                .put(DEFAULT_PRIMARY_KEY, FIELD_WRITE_PERMISSION_CODE);
        log.info(
                "放开权限校验字段,模块={},表={},字段={}",
                moduleFilterInfo.getModuleInfo().getModuleId(),
                moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                DEFAULT_PRIMARY_KEY);
        return tableFieldMap;
    }

    private Map<String, Map<String, Object>> checkAndRemoveUnPermissionField(
            ModuleFilterInfo moduleFilterInfo,
            Map<String, Map<String, Object>> dataMap,
            Boolean isMainModule) {
        Map<String, Map<String, Integer>> tableFieldMap = getFieldPermission(moduleFilterInfo);

        if (isMainModule && CollectionUtil.isEmpty(tableFieldMap)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_FIELD_APPLY_RIGHT_ERROR.getCode(),
                    String.format(
                            "[模块数据新建][%d]模块没有权限", moduleFilterInfo.getModuleInfo().getModuleId()));
        }

        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (isMainModule) {
            for (Map.Entry<String, Map<String, Object>> tableData : dataMap.entrySet()) {
                Map<String, Integer> fieldMap = tableFieldMap.get(tableData.getKey());
                if (CollectionUtil.isEmpty(fieldMap)) {
                    log.warn(
                            String.format(
                                    "[模块数据新建][%d]模块没有表[%s]权限,移除表数据",
                                    moduleFilterInfo.getModuleInfo().getModuleId(),
                                    tableData.getKey()));
                    continue;
                }
                if (!result.containsKey(tableData.getKey())) {
                    result.putIfAbsent(tableData.getKey(), new LinkedHashMap<>());
                }
                for (Map.Entry<String, Object> value : tableData.getValue().entrySet()) {
                    if (fieldMap.containsKey(value.getKey())
                            && FIELD_WRITE_PERMISSION_CODE.equals(fieldMap.get(value.getKey()))) {
                        result.get(tableData.getKey()).put(value.getKey(), value.getValue());
                    } else {
                        log.warn(
                                String.format(
                                        "[模块数据新建][%d]模块没有表[%s]字段[%s]权限,移除字段数据",
                                        moduleFilterInfo.getModuleInfo().getModuleId(),
                                        tableData.getKey(),
                                        value.getKey()));
                    }
                }
                if (CollectionUtil.isEmpty(result.get(tableData.getKey()))) {
                    result.remove(tableData.getKey());
                }
            }
        }
        return result;
    }

    private Boolean hasOperatorPermission(
            List<Long> moduleIdList, Long subjectId, Long roleId, FormButtonEnum formButtonEnum) {

        for (Long id : moduleIdList) {
            // 获取权限过滤后的Field结果
            ModuleFilterInfo moduleFilterInfo =
                    moduleInfoWithPermissionService.filterModuleInfo(
                            subjectId, roleId, id, formButtonEnum.getKey(), List.of());
            if (Objects.isNull(moduleFilterInfo)) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                        String.format("[模块保保存权限校验][%s][%d]模块没有配置定义", formButtonEnum.getKey(), id));
            }
            if (CollectionUtil.isNotEmpty(moduleFilterInfo.getSimpleModuleFields())) {
                for (ModuleField moduleField : moduleFilterInfo.getSimpleModuleFields()) {
                    if (FIELD_WRITE_PERMISSION_CODE.equals(moduleField.getPermissionCode())) {
                        log.info(
                                String.format(
                                        "[模块保存权限校验][%s][%d]模块表[%s]字段[%s]有权限",
                                        formButtonEnum.getKey(),
                                        id,
                                        moduleField.getTableName(),
                                        moduleField.getFieldName()));
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
