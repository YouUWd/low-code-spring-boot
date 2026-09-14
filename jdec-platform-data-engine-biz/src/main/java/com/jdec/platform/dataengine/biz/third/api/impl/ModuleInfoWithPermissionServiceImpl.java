package com.jdec.platform.dataengine.biz.third.api.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.config.api.SysFieldApi;
import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.SysModuleFieldPermissionApi;
import com.jdec.platform.config.api.SysRolePermissionApi;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.dataengine.api.dto.model.TabTreeNodeDTO;
import com.jdec.platform.dataengine.biz.config.dto.ModuleFieldDTO;
import com.jdec.platform.dataengine.biz.config.dto.SysModuleInfo;
import com.jdec.platform.dataengine.biz.config.service.DataModuleConfigService;
import com.jdec.platform.dataengine.biz.dto.*;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.enums.FormButtonEnum;
import com.jdec.platform.dataengine.biz.third.api.module.ModuleInfoWithPermissionService;
import com.jdec.platform.dataengine.biz.util.ToolUtils;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.model.ApiResponse;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@AllArgsConstructor
public class ModuleInfoWithPermissionServiceImpl implements ModuleInfoWithPermissionService {

    private final SysModuleApi sysModuleApi;

    private final DataModuleConfigService dataModuleConfigService;

    private final SysFieldApi sysFieldApi;

    private final SysModuleFieldPermissionApi sysModuleFieldPermissionApi;

    private final SysRolePermissionApi sysRolePermissionApi;

    private static final String DB_TABLE_FIELD_SEPARATOR = ".";

    private static final String COMBINE_FIELD_VIRTUAL_TABLE_NAME = "*";

    private static final String FIELD_TYPE_COMBINE = "COMBINE";

    private static final Integer FIELD_THREE_PERMISSION_CODE = Integer.valueOf(3);

    private static final Integer FIELD_WRITE_PERMISSION_CODE = Integer.valueOf(2);

    private static final Integer FIELD_ONE_PERMISSION_CODE = Integer.valueOf(1);

    private static final Integer FIELD_ZERO_PERMISSION_CODE = Integer.valueOf(0);

    private static final String DEFAULT_PRIMARY_KEY = "id";

    @Override
    public ModuleFilterInfo filterModuleInfo(
            Long subjectId, Long roleId, Long moduleId, String btnType, List<Long> moduleSkip) {

        // 模块相关信息
        SysModuleInfo moduleInfoResp =
                dataModuleConfigService.getModuleCompleteById(
                        AppContext.getProjectNo(), subjectId, moduleId);

        if (Objects.nonNull(moduleInfoResp)) {

            ModuleFilterInfo.ModuleFilterInfoBuilder moduleFilterInfoBuilder =
                    ModuleFilterInfo.builder();

            // 组装ModuleInfo
            ModuleInfo.ModuleInfoBuilder moduleInfoBuilder = ModuleInfo.builder();
            moduleInfoBuilder
                    .moduleId(moduleInfoResp.getModuleId())
                    .moduleSlug(moduleInfoResp.getModuleCode())
                    .primaryTable(moduleInfoResp.getPrimaryTable())
                    .sourceSubjects(Arrays.asList(subjectId))
                    //                    .sourceSubjects(moduleInfoResp.getSourceSubjects())
                    //
                    //
                    // .relateSearchField(moduleInfoResp.getModule().getRelateSearchField())
                    .moduleType("LIST")
                    .moduleName(moduleInfoResp.getModuleName());

            List<ModuleField> allFields = new ArrayList<>();

            // 获取模块 表的列配置
            Map<String, SysFieldResp> sysFieldRespMap = new HashMap<>();
            Set<String> tableNames = new HashSet<>();

            tableNames.addAll(
                    moduleInfoResp.getFields().stream()
                            .map(x -> x.getTableName())
                            .collect(Collectors.toSet()));

            for (String tableName : tableNames) {
                List<SysFieldResp> fieldResps =
                        sysFieldApi.querySimpleSysFields(
                                AppContext.getProjectNo(), subjectId, tableName, null);
                if (CollectionUtil.isNotEmpty(fieldResps)) {
                    for (SysFieldResp fieldResp : fieldResps) {
                        sysFieldRespMap.put(
                                fieldResp.getTableName() + "." + fieldResp.getColumnName(),
                                fieldResp);
                    }
                }
            }
            SysFieldResp defaultSysFieldResp = new SysFieldResp();

            for (ModuleFieldDTO simpleFieldItem : moduleInfoResp.getFields()) {

                SysFieldResp sysFieldResp =
                        sysFieldRespMap.getOrDefault(
                                simpleFieldItem.getTableName()
                                        + "."
                                        + simpleFieldItem.getColumnName(),
                                defaultSysFieldResp);
                List<FieldChainValueDTO> fieldChainValueDTOS = new ArrayList<>();
                if (CollectionUtil.isNotEmpty(sysFieldResp.getFieldValues())) {
                    fieldChainValueDTOS =
                            sysFieldResp.getFieldValues().stream()
                                    .filter(
                                            x ->
                                                    moduleId.equals(x.getModuleId())
                                                            && x.getEnabled() != null
                                                            && x.getEnabled()
                                                                    .equals(Integer.valueOf(1)))
                                    .map(
                                            x ->
                                                    FieldChainValueDTO.builder()
                                                            .fieldValue(x.getFieldValue())
                                                            .fieldValueDesc(x.getFieldValueDesc())
                                                            .approvalChainTypeId(
                                                                    x.getApprovalChainTypeId())
                                                            .approvalChainTypeName(
                                                                    x.getApprovalChainTypeName())
                                                            .build())
                                    .collect(Collectors.toList());
                }
                ModuleField mf =
                        ModuleField.builder()
                                .fieldCode(simpleFieldItem.getColumnName())
                                .fieldName(simpleFieldItem.getDisplayName())
                                .moduleId(moduleInfoResp.getModuleId())
                                .permissionCode(0)
                                .filedType("SIMPLE")
                                .tableName(simpleFieldItem.getTableName())
                                .bizKeyOrder(simpleFieldItem.getBizKeyOrder())
                                .relationBusinessNo(sysFieldResp.getRelationBusinessNo())
                                .relationName(sysFieldResp.getRelationName())
                                .encrypted(sysFieldResp.getEncrypted())
                                .dataRightFlag(sysFieldResp.getDataRightFlag())
                                .fieldChainValues(fieldChainValueDTOS)
                                .build();

                allFields.add(mf);
            }

            moduleFilterInfoBuilder.allFields(allFields);

            Long btnListJudgeModuleId = null;

            //            Long btnListJudgeModuleId =
            // moduleInfoResp.getModule().getDetailModuleId();

            boolean created = StringUtils.hasText(btnType) ? true : false;

            Set<FormButton> btnList = new HashSet<>();

            List<ModuleColumnResp> detailCreateFields = new ArrayList<>();

            List<ModuleColumnResp> detailEditFields = new ArrayList<>();

            List<ModuleColumnResp> detailReadFields = new ArrayList<>();

            if (Objects.nonNull(btnListJudgeModuleId)) {

                log.debug(
                        "列表模块id:{}设置的详情模块id:{}不为空，详情模块开始递归查找的权限节点判断按钮返回列表。",
                        moduleId,
                        btnListJudgeModuleId);

                List<SysModuleSimpleTreeResp> moduleNodeResps =
                        sysModuleApi.getAvailableModuleTree(
                                null, AppContext.getProjectNo(), subjectId);

                btnType =
                        getFinalBtn(
                                moduleNodeResps,
                                btnListJudgeModuleId,
                                subjectId,
                                roleId,
                                btnType,
                                btnList,
                                created,
                                moduleSkip);
            }

            // 模块表权限节点
            List<PermissionTableGroupResp> moduleColumnPermission =
                    sysModuleFieldPermissionApi.getRoleModuleFieldPermissions(
                            AppContext.getProjectNo(), subjectId, roleId, moduleId);

            if (!CollectionUtils.isEmpty(moduleColumnPermission)) {

                // 权限节点不来自详情模块，查询本模块权限节点
                if (CollectionUtils.isEmpty(btnList)) {

                    if (Objects.nonNull(btnListJudgeModuleId)
                            && CollectionUtils.isEmpty(detailCreateFields)
                            && CollectionUtils.isEmpty(detailEditFields)
                            && CollectionUtils.isEmpty(detailReadFields)) {
                        log.debug("详情模块无任何权限导致btnList为空，此时不需要判断本模块的权限");
                    } else if (Objects.isNull(btnListJudgeModuleId)
                    // && "LIST".equals(moduleInfoResp.getModule().getModuleType())
                    ) {
                        log.debug("模块为列表类型且未配置详情模块，此时不需要判断本模块的权限，直接返回无权限。");
                        btnType = "view";
                    } else {
                        List<ModuleColumnResp> createFields = new ArrayList<>();

                        List<ModuleColumnResp> editFields = new ArrayList<>();

                        List<ModuleColumnResp> readFields = new ArrayList<>();

                        moduleColumnPermission.stream()
                                .forEach(
                                        ele ->
                                                createFields.addAll(
                                                        ele.getWritableFields().stream()
                                                                .map(
                                                                        item ->
                                                                                new ModuleColumnResp(
                                                                                        item
                                                                                                .getId(),
                                                                                        item
                                                                                                .getColumnName(),
                                                                                        item
                                                                                                .getDisplayName()))
                                                                .collect(Collectors.toList())));

                        moduleColumnPermission.stream()
                                .forEach(
                                        ele ->
                                                editFields.addAll(
                                                        ele.getUpdatableFields().stream()
                                                                .map(
                                                                        item ->
                                                                                new ModuleColumnResp(
                                                                                        item
                                                                                                .getId(),
                                                                                        item
                                                                                                .getColumnName(),
                                                                                        item
                                                                                                .getDisplayName()))
                                                                .collect(Collectors.toList())));

                        moduleColumnPermission.stream()
                                .forEach(
                                        ele ->
                                                readFields.addAll(
                                                        ele.getReadableFields().stream()
                                                                .map(
                                                                        item ->
                                                                                new ModuleColumnResp(
                                                                                        item
                                                                                                .getId(),
                                                                                        item
                                                                                                .getColumnName(),
                                                                                        item
                                                                                                .getDisplayName()))
                                                                .collect(Collectors.toList())));

                        if (!CollectionUtils.isEmpty(createFields)) {
                            btnList.add(
                                    FormButton.builder()
                                            .buttonKey(FormButtonEnum.CREATE.getKey())
                                            .buttonName(FormButtonEnum.CREATE.getName())
                                            .build());
                            // 有传入参数则不赋值，对应于创建接口调用
                            if (!StringUtils.hasText(btnType)) {
                                btnType = "create";
                            }
                        }

                        // 编辑和查看二选一
                        if (!CollectionUtils.isEmpty(editFields)) {
                            btnList.add(
                                    FormButton.builder()
                                            .buttonKey(FormButtonEnum.EDITE.getKey())
                                            .buttonName(FormButtonEnum.EDITE.getName())
                                            .build());
                            if (!created) {
                                btnType = "edit";
                            }
                        } else if (!CollectionUtils.isEmpty(readFields)) {
                            btnList.add(
                                    FormButton.builder()
                                            .buttonKey(FormButtonEnum.VIEW.getKey())
                                            .buttonName(FormButtonEnum.VIEW.getName())
                                            .build());
                            if (!created) {
                                btnType = "view";
                            }
                        }
                    }
                }

                moduleFilterInfoBuilder.btnList(btnList);

                // 按tableName分组
                Map<String, PermissionTableGroupResp> tableNameToModuleColumnPermission =
                        moduleColumnPermission.stream()
                                .collect(
                                        Collectors.groupingBy(
                                                PermissionTableGroupResp::getTableName,
                                                Collectors.collectingAndThen(
                                                        Collectors.toList(),
                                                        value -> value.get(0))));

                Map<String, Map<String, Set<RightNode>>> tableNameToThreeRightSetMap =
                        new HashMap<>();

                for (PermissionTableGroupResp ele : moduleColumnPermission) {

                    Map<String, Set<RightNode>> threeRightSetMap = new HashMap<>();

                    // 放入有查看权限的字段
                    threeRightSetMap.put(
                            "READ",
                            ele.getReadableFields().stream()
                                    .map(e -> new RightNode(e.getColumnName(), e.getDisplayName()))
                                    .collect(Collectors.toSet()));

                    // 放入有编辑权限的字段
                    threeRightSetMap.put(
                            "EDIT",
                            ele.getUpdatableFields().stream()
                                    .map(e -> new RightNode(e.getColumnName(), e.getDisplayName()))
                                    .collect(Collectors.toSet()));

                    // 放入有创建权限的字段
                    threeRightSetMap.put(
                            "CREATE",
                            ele.getWritableFields().stream()
                                    .map(e -> new RightNode(e.getColumnName(), e.getDisplayName()))
                                    .collect(Collectors.toSet()));

                    tableNameToThreeRightSetMap.put(ele.getTableName(), threeRightSetMap);
                }

                // tableName为key所有允许的权限字段集合，三种合并
                Map<String, Set<String>> tableNameToAllowedFields = new HashMap<>();

                // 不区分TableName的权限字段集合
                Set<String> allAllowedRightFields = new HashSet<>();

                for (Map.Entry<String, Map<String, Set<RightNode>>> entry :
                        tableNameToThreeRightSetMap.entrySet()) {
                    Set<String> allowedRightFields = new HashSet<>();
                    Map<String, Set<RightNode>> rightMap = entry.getValue();
                    for (Map.Entry<String, Set<RightNode>> rightMapEntry : rightMap.entrySet()) {
                        allowedRightFields.addAll(
                                rightMapEntry.getValue().stream()
                                        .map(
                                                ele -> {
                                                    allAllowedRightFields.add(
                                                            entry.getKey()
                                                                    + DB_TABLE_FIELD_SEPARATOR
                                                                    + ele.fieldCode);
                                                    return ele.fieldCode;
                                                })
                                        .collect(Collectors.toList()));
                    }
                    tableNameToAllowedFields.put(entry.getKey(), allowedRightFields);
                }

                // 过滤SimpleField，只查询有权限的字段
                List<ModuleFieldDTO> filteredSimpleFields =
                        moduleInfoResp.getFields().stream()
                                .filter(
                                        e ->
                                                tableNameToAllowedFields.containsKey(
                                                                e.getTableName())
                                                        && tableNameToAllowedFields
                                                                .get(e.getTableName())
                                                                .contains(e.getColumnName()))
                                .collect(Collectors.toList());

                // 如果是列表类型，只判断查看权限，手动赋值btnType为view
                //                if ("LIST".equals(moduleInfoResp.getModule().getModuleType())) {
                //                    btnType = "view";
                //                }

                FormButtonEnum buttonEnum = FormButtonEnum.getFormButtonEnumByKey(btnType);

                // 获取模块对应的互动权限节点
                RoleInteractionPermissionByModuleResp interOperateRightResp =
                        sysRolePermissionApi.getRoleInteractionPermissionByModule(roleId, moduleId);

                Set<InterOperateRight> interOperateRights = new HashSet<>();

                if (Objects.nonNull(buttonEnum) && Objects.nonNull(interOperateRightResp)) {
                    switch (buttonEnum) {
                        case FormButtonEnum.VIEW -> {
                            interOperateRightResp.getViewPermissions().stream()
                                    .forEach(
                                            ele -> {
                                                interOperateRights.add(
                                                        InterOperateRight.builder()
                                                                .rightCode(ele.getPermissionCode())
                                                                .rightName(ele.getPermissionName())
                                                                .build());
                                            });
                        }
                        case FormButtonEnum.EDITE -> {
                            // 在view中没在edit中的全部加入到集合中
                            interOperateRightResp.getViewPermissions().stream()
                                    .filter(
                                            ele ->
                                                    !interOperateRightResp
                                                            .getEditPermissions()
                                                            .stream()
                                                            .anyMatch(
                                                                    item ->
                                                                            item.getPermissionCode()
                                                                                    .equals(
                                                                                            ele
                                                                                                    .getPermissionCode())))
                                    .forEach(
                                            ele -> {
                                                interOperateRights.add(
                                                        InterOperateRight.builder()
                                                                .rightCode(ele.getPermissionCode())
                                                                .rightName(ele.getPermissionName())
                                                                .build());
                                            });

                            // edit中的全部加入到集合
                            interOperateRightResp.getEditPermissions().stream()
                                    .forEach(
                                            ele -> {
                                                interOperateRights.add(
                                                        InterOperateRight.builder()
                                                                .rightCode(ele.getPermissionCode())
                                                                .rightName(ele.getPermissionName())
                                                                .build());
                                            });
                        }
                        case FormButtonEnum.CREATE -> {
                            // 在view中没在create中的全部加入到集合中
                            interOperateRightResp.getViewPermissions().stream()
                                    .filter(
                                            ele ->
                                                    !interOperateRightResp
                                                            .getEditPermissions()
                                                            .stream()
                                                            .anyMatch(
                                                                    item ->
                                                                            item.getPermissionCode()
                                                                                    .equals(
                                                                                            ele
                                                                                                    .getPermissionCode())))
                                    .forEach(
                                            ele -> {
                                                interOperateRights.add(
                                                        InterOperateRight.builder()
                                                                .rightCode(ele.getPermissionCode())
                                                                .rightName(ele.getPermissionName())
                                                                .build());
                                            });

                            // create中的全部加入到集合
                            interOperateRightResp.getApplyPermissions().stream()
                                    .forEach(
                                            ele -> {
                                                interOperateRights.add(
                                                        InterOperateRight.builder()
                                                                .rightCode(ele.getPermissionCode())
                                                                .rightName(ele.getPermissionName())
                                                                .build());
                                            });
                        }
                    }
                }

                // 将互动权限放入返回对象
                moduleFilterInfoBuilder.interOperateRights(interOperateRights);

                // 填充permissionCode和fieldName字段后的simpleField
                List<ModuleField> simpleFieldInfo = new ArrayList<>();

                // 填充permissionCode和fieldName
                for (ModuleFieldDTO simpleField : filteredSimpleFields) {
                    SysFieldResp sysFieldResp =
                            sysFieldRespMap.getOrDefault(
                                    simpleField.getTableName() + "." + simpleField.getColumnName(),
                                    defaultSysFieldResp);
                    List<FieldChainValueDTO> fieldChainValueDTOS = new ArrayList<>();
                    if (CollectionUtil.isNotEmpty(sysFieldResp.getFieldValues())) {
                        fieldChainValueDTOS =
                                sysFieldResp.getFieldValues().stream()
                                        .filter(
                                                x ->
                                                        moduleId.equals(x.getModuleId())
                                                                && x.getEnabled() != null
                                                                && x.getEnabled()
                                                                        .equals(Integer.valueOf(1)))
                                        .map(
                                                x ->
                                                        FieldChainValueDTO.builder()
                                                                .fieldValue(x.getFieldValue())
                                                                .fieldValueDesc(
                                                                        x.getFieldValueDesc())
                                                                .approvalChainTypeId(
                                                                        x.getApprovalChainTypeId())
                                                                .approvalChainTypeName(
                                                                        x
                                                                                .getApprovalChainTypeName())
                                                                .build())
                                        .collect(Collectors.toList());
                    }
                    ModuleField.ModuleFieldBuilder moduleFieldBuilder =
                            ModuleField.builder()
                                    .moduleId(moduleInfoResp.getModuleId())
                                    .filedType("SIMPLE")
                                    .fieldCode(simpleField.getColumnName())
                                    .tableName(simpleField.getTableName())
                                    .relationBusinessNo(sysFieldResp.getRelationBusinessNo())
                                    .relationName(sysFieldResp.getRelationName())
                                    .encrypted(sysFieldResp.getEncrypted())
                                    .dataRightFlag(sysFieldResp.getDataRightFlag())
                                    .fieldChainValues(fieldChainValueDTOS);

                    Integer permissionCode = 0;
                    String fieldName = simpleField.getColumnName();

                    // 根据模块角色权限，填充permissionCode和fieldName字段
                    Set<RightNode> readFields =
                            tableNameToThreeRightSetMap.containsKey(simpleField.getTableName())
                                    ? tableNameToThreeRightSetMap
                                            .get(simpleField.getTableName())
                                            .get("READ")
                                    : new HashSet<>();
                    Set<RightNode> editFields =
                            tableNameToThreeRightSetMap.containsKey(simpleField.getTableName())
                                    ? tableNameToThreeRightSetMap
                                            .get(simpleField.getTableName())
                                            .get("EDIT")
                                    : new HashSet<>();
                    Set<RightNode> createFields =
                            tableNameToThreeRightSetMap.containsKey(simpleField.getTableName())
                                    ? tableNameToThreeRightSetMap
                                            .get(simpleField.getTableName())
                                            .get("CREATE")
                                    : new HashSet<>();

                    // 根据权限勾选情况，设置permissionCode的值

                    boolean finded = false;

                    if (Objects.nonNull(buttonEnum)) {
                        switch (buttonEnum) {
                            case FormButtonEnum.VIEW -> {
                                // 在查看权限查找,若有匹配则无需判断其他两种权限
                                for (RightNode rightNode : readFields) {
                                    if (rightNode.fieldCode.equals(simpleField.getColumnName())) {
                                        finded = true;
                                        permissionCode = 1;
                                        fieldName = rightNode.fieldName();
                                        break;
                                    }
                                }
                                break;
                            }
                            case FormButtonEnum.EDITE -> {
                                // 先在编辑权限查找,若有匹配则无需判断其他两种权限
                                for (RightNode rightNode : editFields) {
                                    if (rightNode.fieldCode.equals(simpleField.getColumnName())) {
                                        finded = true;
                                        permissionCode = 2;
                                        fieldName = rightNode.fieldName();
                                        break;
                                    }
                                }

                                // 编辑权限没有，则在查看权限匹配
                                if (!finded) {
                                    for (RightNode rightNode : readFields) {
                                        if (rightNode.fieldCode.equals(
                                                simpleField.getColumnName())) {
                                            finded = true;
                                            permissionCode = 1;
                                            fieldName = rightNode.fieldName();
                                            break;
                                        }
                                    }
                                }
                            }
                            case FormButtonEnum.CREATE -> {
                                // 先在编辑权限查找,若有匹配则无需判断其他两种权限
                                for (RightNode rightNode : createFields) {
                                    if (rightNode.fieldCode.equals(simpleField.getColumnName())) {
                                        finded = true;
                                        permissionCode = 2;
                                        fieldName = rightNode.fieldName();
                                        break;
                                    }
                                }

                                // 编辑权限没有，则在查看权限匹配
                                if (!finded) {
                                    for (RightNode rightNode : readFields) {
                                        if (rightNode.fieldCode.equals(
                                                simpleField.getColumnName())) {
                                            finded = true;
                                            permissionCode = 1;
                                            fieldName = rightNode.fieldName();
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 有匹配的字段才放入List
                    if (finded) {
                        moduleFieldBuilder.permissionCode(permissionCode);
                        moduleFieldBuilder.fieldName(fieldName);

                        simpleFieldInfo.add(moduleFieldBuilder.build());
                    }
                }

                // 填充simpleModuleFields属性
                moduleFilterInfoBuilder.simpleModuleFields(simpleFieldInfo);
            }

            // moduleInfo数据填充完毕，写入到ModuleFilterInfo
            moduleFilterInfoBuilder.moduleInfo(moduleInfoBuilder.build());

            TabTreeNodeDTO tableTree = TabTreeNodeDTO.buildRoot();

            TabTreeNodeDTO targetNode =
                    TabTreeNodeDTO.findSubTreeByValue(tableTree, moduleInfoResp.getPrimaryTable());

            Set<String> subTableNames =
                    moduleInfoResp.getFields().stream()
                            .filter(
                                    ele ->
                                            !moduleInfoResp
                                                    .getPrimaryTable()
                                                    .equals(ele.getTableName()))
                            .map(ModuleFieldDTO::getTableName)
                            .collect(Collectors.toSet());

            moduleFilterInfoBuilder.moduleTableList(
                    subTableNames.stream()
                            .map(
                                    ele -> {
                                        TabTreeNodeDTO matchedNode =
                                                targetNode.getChildren().stream()
                                                        .filter(
                                                                item ->
                                                                        ele.equals(
                                                                                item.getNode()
                                                                                        .getTableName()))
                                                        .findFirst()
                                                        .orElse(null);
                                        if (Objects.nonNull(matchedNode)) {
                                            return ModuleTable.builder()
                                                    .moduleId(moduleId)
                                                    .tableName(ele)
                                                    .joinLeftField(
                                                            matchedNode
                                                                    .getNode()
                                                                    .getSubTableField())
                                                    .joinRightField(
                                                            matchedNode
                                                                    .getNode()
                                                                    .getParentTableField())
                                                    .relationType(
                                                            matchedNode.getNode().getJoinType())
                                                    .build();
                                        } else {
                                            return null;
                                        }
                                    })
                            .collect(Collectors.toList()));

            return moduleFilterInfoBuilder.build();
        }

        return null;
    }

    @Override
    public ModuleFilterInfo filterModuleInfoWithApproval(
            Long subjectId, Long roleId, Long moduleId, String btnType, List<Long> moduleSkip) {
        // 模块相关信息
        SysModuleInfo moduleInfoResp =
                dataModuleConfigService.getModuleCompleteById(
                        AppContext.getProjectNo(), subjectId, moduleId);

        if (Objects.nonNull(moduleInfoResp)) {

            ModuleFilterInfo.ModuleFilterInfoBuilder moduleFilterInfoBuilder =
                    ModuleFilterInfo.builder();

            // 组装ModuleInfo
            ModuleInfo.ModuleInfoBuilder moduleInfoBuilder = ModuleInfo.builder();
            moduleInfoBuilder
                    .moduleId(moduleInfoResp.getModuleId().longValue())
                    .moduleSlug(moduleInfoResp.getModuleCode())
                    .primaryTable(moduleInfoResp.getPrimaryTable())
                    .sourceSubjects(Arrays.asList(subjectId))
                    //
                    // .relateSearchField(moduleInfoResp.getModule().getRelateSearchField())
                    .moduleType("LIST")
                    .moduleName(moduleInfoResp.getModuleName());

            List<ModuleField> allFields = new ArrayList<>();

            for (ModuleFieldDTO simpleFieldItem : moduleInfoResp.getFields()) {
                allFields.add(
                        ModuleField.builder()
                                .fieldCode(simpleFieldItem.getColumnName())
                                .fieldName(simpleFieldItem.getDisplayName())
                                .moduleId(moduleInfoResp.getModuleId())
                                .permissionCode(0)
                                .filedType("SIMPLE")
                                .tableName(simpleFieldItem.getTableName())
                                .build());
            }

            moduleFilterInfoBuilder.allFields(allFields);

            Long btnListJudgeModuleId = null;

            //            Long btnListJudgeModuleId =
            // moduleInfoResp.getModule().getDetailModuleId();

            List<ModuleColumnResp> detailCreateFields = new ArrayList<>();

            List<ModuleColumnResp> detailEditFields = new ArrayList<>();

            List<ModuleColumnResp> detailReadFields = new ArrayList<>();

            // 模块表权限节点
            // 模块表权限节点
            List<PermissionTableGroupResp> moduleColumnPermission =
                    sysModuleFieldPermissionApi.getRoleModuleFieldPermissions(
                            AppContext.getProjectNo(), subjectId, roleId, moduleId);

            if (!CollectionUtils.isEmpty(moduleColumnPermission)) {

                // 按tableName分组
                Map<String, PermissionTableGroupResp> tableNameToModuleColumnPermission =
                        moduleColumnPermission.stream()
                                .collect(
                                        Collectors.groupingBy(
                                                PermissionTableGroupResp::getTableName,
                                                Collectors.collectingAndThen(
                                                        Collectors.toList(),
                                                        value -> value.get(0))));

                Map<String, Map<String, Set<RightNode>>> tableNameToThreeRightSetMap =
                        new HashMap<>();

                for (PermissionTableGroupResp ele : moduleColumnPermission) {

                    Map<String, Set<RightNode>> threeRightSetMap = new HashMap<>();

                    // 放入有查看权限的字段
                    threeRightSetMap.put(
                            "READ",
                            ele.getReadableFields().stream()
                                    .map(e -> new RightNode(e.getColumnName(), e.getDisplayName()))
                                    .collect(Collectors.toSet()));

                    // 放入有编辑权限的字段
                    threeRightSetMap.put(
                            "EDIT",
                            ele.getUpdatableFields().stream()
                                    .map(e -> new RightNode(e.getColumnName(), e.getDisplayName()))
                                    .collect(Collectors.toSet()));

                    // 放入有创建权限的字段
                    threeRightSetMap.put(
                            "CREATE",
                            ele.getWritableFields().stream()
                                    .map(e -> new RightNode(e.getColumnName(), e.getDisplayName()))
                                    .collect(Collectors.toSet()));

                    tableNameToThreeRightSetMap.put(ele.getTableName(), threeRightSetMap);
                }

                // tableName为key所有允许的权限字段集合，三种合并
                Map<String, Set<String>> tableNameToAllowedFields = new HashMap<>();

                // 不区分TableName的权限字段集合
                Set<String> allAllowedRightFields = new HashSet<>();

                for (Map.Entry<String, Map<String, Set<RightNode>>> entry :
                        tableNameToThreeRightSetMap.entrySet()) {
                    Set<String> allowedRightFields = new HashSet<>();
                    Map<String, Set<RightNode>> rightMap = entry.getValue();
                    for (Map.Entry<String, Set<RightNode>> rightMapEntry : rightMap.entrySet()) {
                        allowedRightFields.addAll(
                                rightMapEntry.getValue().stream()
                                        .map(
                                                ele -> {
                                                    allAllowedRightFields.add(
                                                            entry.getKey()
                                                                    + DB_TABLE_FIELD_SEPARATOR
                                                                    + ele.fieldCode);
                                                    return ele.fieldCode;
                                                })
                                        .collect(Collectors.toList()));
                    }
                    tableNameToAllowedFields.put(entry.getKey(), allowedRightFields);
                }

                // 过滤SimpleField，只查询有权限的字段
                List<ModuleFieldDTO> filteredSimpleFields =
                        moduleInfoResp.getFields().stream()
                                .filter(
                                        e ->
                                                tableNameToAllowedFields.containsKey(
                                                                e.getTableName())
                                                        && tableNameToAllowedFields
                                                                .get(e.getTableName())
                                                                .contains(e.getColumnName()))
                                .collect(Collectors.toList());

                // 如果是列表类型，只判断查看权限，手动赋值btnType为view
                //                if ("LIST".equals(moduleInfoResp.getModule().getModuleType())) {
                //                    btnType = "view";
                //                } else if (!StringUtils.hasText(btnType)) {
                //                    btnType = "view";
                //                }

                FormButtonEnum buttonEnum = FormButtonEnum.getFormButtonEnumByKey(btnType);

                // 获取模块对应的互动权限节点
                RoleInteractionPermissionByModuleResp interOperateRightResp =
                        sysRolePermissionApi.getRoleInteractionPermissionByModule(roleId, moduleId);

                Set<InterOperateRight> interOperateRights = new HashSet<>();

                if (Objects.nonNull(buttonEnum) && Objects.nonNull(interOperateRightResp)) {
                    switch (buttonEnum) {
                        case FormButtonEnum.VIEW -> {
                            interOperateRightResp.getViewPermissions().stream()
                                    .forEach(
                                            ele -> {
                                                interOperateRights.add(
                                                        InterOperateRight.builder()
                                                                .rightCode(ele.getPermissionCode())
                                                                .rightName(ele.getPermissionName())
                                                                .build());
                                            });
                        }
                        case FormButtonEnum.EDITE -> {
                            // 在view中没在edit中的全部加入到集合中
                            interOperateRightResp.getViewPermissions().stream()
                                    .filter(
                                            ele ->
                                                    !interOperateRightResp
                                                            .getEditPermissions()
                                                            .stream()
                                                            .anyMatch(
                                                                    item ->
                                                                            item.getPermissionCode()
                                                                                    .equals(
                                                                                            ele
                                                                                                    .getPermissionCode())))
                                    .forEach(
                                            ele -> {
                                                interOperateRights.add(
                                                        InterOperateRight.builder()
                                                                .rightCode(ele.getPermissionCode())
                                                                .rightName(ele.getPermissionName())
                                                                .build());
                                            });

                            // edit中的全部加入到集合
                            interOperateRightResp.getEditPermissions().stream()
                                    .forEach(
                                            ele -> {
                                                interOperateRights.add(
                                                        InterOperateRight.builder()
                                                                .rightCode(ele.getPermissionCode())
                                                                .rightName(ele.getPermissionName())
                                                                .build());
                                            });
                        }
                        case FormButtonEnum.CREATE -> {
                            // 在view中没在create中的全部加入到集合中
                            interOperateRightResp.getViewPermissions().stream()
                                    .filter(
                                            ele ->
                                                    !interOperateRightResp
                                                            .getEditPermissions()
                                                            .stream()
                                                            .anyMatch(
                                                                    item ->
                                                                            item.getPermissionCode()
                                                                                    .equals(
                                                                                            ele
                                                                                                    .getPermissionCode())))
                                    .forEach(
                                            ele -> {
                                                interOperateRights.add(
                                                        InterOperateRight.builder()
                                                                .rightCode(ele.getPermissionCode())
                                                                .rightName(ele.getPermissionName())
                                                                .build());
                                            });

                            // create中的全部加入到集合
                            interOperateRightResp.getApplyPermissions().stream()
                                    .forEach(
                                            ele -> {
                                                interOperateRights.add(
                                                        InterOperateRight.builder()
                                                                .rightCode(ele.getPermissionCode())
                                                                .rightName(ele.getPermissionName())
                                                                .build());
                                            });
                        }
                    }
                }

                // 将互动权限放入返回对象
                moduleFilterInfoBuilder.interOperateRights(interOperateRights);

                // 填充permissionCode和fieldName字段后的simpleField
                List<ModuleField> simpleFieldInfo = new ArrayList<>();

                // 填充permissionCode和fieldName
                for (ModuleFieldDTO simpleField : filteredSimpleFields) {
                    ModuleField.ModuleFieldBuilder moduleFieldBuilder =
                            ModuleField.builder()
                                    .moduleId(moduleInfoResp.getModuleId())
                                    .filedType("SIMPLE")
                                    .fieldCode(simpleField.getColumnName())
                                    .tableName(simpleField.getTableName());

                    Integer permissionCode = 0;
                    String fieldName = simpleField.getColumnName();

                    // 根据模块角色权限，填充permissionCode和fieldName字段
                    Set<RightNode> readFields =
                            tableNameToThreeRightSetMap.containsKey(simpleField.getTableName())
                                    ? tableNameToThreeRightSetMap
                                            .get(simpleField.getTableName())
                                            .get("READ")
                                    : new HashSet<>();
                    Set<RightNode> editFields =
                            tableNameToThreeRightSetMap.containsKey(simpleField.getTableName())
                                    ? tableNameToThreeRightSetMap
                                            .get(simpleField.getTableName())
                                            .get("EDIT")
                                    : new HashSet<>();
                    Set<RightNode> createFields =
                            tableNameToThreeRightSetMap.containsKey(simpleField.getTableName())
                                    ? tableNameToThreeRightSetMap
                                            .get(simpleField.getTableName())
                                            .get("CREATE")
                                    : new HashSet<>();

                    // 根据权限勾选情况，设置permissionCode的值

                    boolean finded = false;

                    if (Objects.nonNull(buttonEnum)) {
                        switch (buttonEnum) {
                            case FormButtonEnum.VIEW -> {
                                // 在查看权限查找,若有匹配则无需判断其他两种权限
                                for (RightNode rightNode : readFields) {
                                    if (rightNode.fieldCode.equals(simpleField.getColumnName())) {
                                        finded = true;
                                        permissionCode = 1;
                                        fieldName = rightNode.fieldName();
                                        break;
                                    }
                                }
                                break;
                            }
                            case FormButtonEnum.EDITE -> {
                                // 先在编辑权限查找,若有匹配则无需判断其他两种权限
                                for (RightNode rightNode : editFields) {
                                    if (rightNode.fieldCode.equals(simpleField.getColumnName())) {
                                        finded = true;
                                        permissionCode = 2;
                                        fieldName = rightNode.fieldName();
                                        break;
                                    }
                                }

                                // 编辑权限没有，则在查看权限匹配
                                if (!finded) {
                                    for (RightNode rightNode : readFields) {
                                        if (rightNode.fieldCode.equals(
                                                simpleField.getColumnName())) {
                                            finded = true;
                                            permissionCode = 1;
                                            fieldName = rightNode.fieldName();
                                            break;
                                        }
                                    }
                                }
                            }
                            case FormButtonEnum.CREATE -> {
                                // 先在编辑权限查找,若有匹配则无需判断其他两种权限
                                for (RightNode rightNode : createFields) {
                                    if (rightNode.fieldCode.equals(simpleField.getColumnName())) {
                                        finded = true;
                                        permissionCode = 2;
                                        fieldName = rightNode.fieldName();
                                        break;
                                    }
                                }

                                // 编辑权限没有，则在查看权限匹配
                                if (!finded) {
                                    for (RightNode rightNode : readFields) {
                                        if (rightNode.fieldCode.equals(
                                                simpleField.getColumnName())) {
                                            finded = true;
                                            permissionCode = 1;
                                            fieldName = rightNode.fieldName();
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 有匹配的字段才放入List
                    if (finded) {
                        moduleFieldBuilder.permissionCode(permissionCode);
                        moduleFieldBuilder.fieldName(fieldName);

                        simpleFieldInfo.add(moduleFieldBuilder.build());
                    }
                }

                // 填充simpleModuleFields属性
                moduleFilterInfoBuilder.simpleModuleFields(simpleFieldInfo);
            }

            // moduleInfo数据填充完毕，写入到ModuleFilterInfo
            moduleFilterInfoBuilder.moduleInfo(moduleInfoBuilder.build());

            TabTreeNodeDTO tableTree = TabTreeNodeDTO.buildRoot();

            TabTreeNodeDTO targetNode =
                    TabTreeNodeDTO.findSubTreeByValue(tableTree, moduleInfoResp.getPrimaryTable());

            Set<String> subTableNames =
                    moduleInfoResp.getFields().stream()
                            .filter(
                                    ele ->
                                            !moduleInfoResp
                                                    .getPrimaryTable()
                                                    .equals(ele.getTableName()))
                            .map(ModuleFieldDTO::getTableName)
                            .collect(Collectors.toSet());

            moduleFilterInfoBuilder.moduleTableList(
                    subTableNames.stream()
                            .map(
                                    ele -> {
                                        TabTreeNodeDTO matchedNode =
                                                targetNode.getChildren().stream()
                                                        .filter(
                                                                item ->
                                                                        ele.equals(
                                                                                item.getNode()
                                                                                        .getTableName()))
                                                        .findFirst()
                                                        .orElse(null);
                                        if (Objects.nonNull(matchedNode)) {
                                            return ModuleTable.builder()
                                                    .moduleId(moduleId)
                                                    .tableName(ele)
                                                    .joinLeftField(
                                                            matchedNode
                                                                    .getNode()
                                                                    .getSubTableField())
                                                    .joinRightField(
                                                            matchedNode
                                                                    .getNode()
                                                                    .getParentTableField())
                                                    .relationType(
                                                            matchedNode.getNode().getJoinType())
                                                    .build();
                                        } else {
                                            return null;
                                        }
                                    })
                            .collect(Collectors.toList()));

            return moduleFilterInfoBuilder.build();
        }

        return null;
    }

    private String getFinalBtn(
            List<SysModuleSimpleTreeResp> moduleNodeResps,
            Long parentModuleId,
            Long subjectId,
            Long roleId,
            String btnType,
            Set<FormButton> btnList,
            boolean created,
            List<Long> moduleSkip) {
        log.debug("跳过判断的模块id:{}", moduleSkip);
        List<SysModuleSimpleTreeResp> subModuleList = new ArrayList<>();

        SysModuleSimpleTreeResp targetModule =
                ToolUtils.findNodeByTreeRecursion(moduleNodeResps, parentModuleId);

        ToolUtils.flatNodeAndChildren(targetModule, subModuleList);

        boolean createFinded = false;
        boolean editFinded = false;
        for (SysModuleSimpleTreeResp node : subModuleList) {

            if (!CollectionUtils.isEmpty(moduleSkip)
                    && moduleSkip.stream().anyMatch(ele -> ele.equals(node.getId()))) {
                log.debug("子模块id:{}在跳过列表中，不参与按钮状态判断。", node.getId());
                continue;
            }

            // 模块表权限节点
            List<PermissionTableGroupResp> moduleColumnPermission =
                    sysModuleFieldPermissionApi.getRoleModuleFieldPermissions(
                            AppContext.getProjectNo(), subjectId, roleId, node.getId());

            List<ModuleColumnResp> detailCreateFields = new ArrayList<>();

            List<ModuleColumnResp> detailEditFields = new ArrayList<>();

            List<ModuleColumnResp> detailReadFields = new ArrayList<>();

            moduleColumnPermission.stream()
                    .forEach(
                            ele ->
                                    detailCreateFields.addAll(
                                            ele.getWritableFields().stream()
                                                    .map(
                                                            item ->
                                                                    new ModuleColumnResp(
                                                                            item.getId(),
                                                                            item.getColumnName(),
                                                                            item.getDisplayName()))
                                                    .collect(Collectors.toList())));

            moduleColumnPermission.stream()
                    .forEach(
                            ele ->
                                    detailEditFields.addAll(
                                            ele.getUpdatableFields().stream()
                                                    .map(
                                                            item ->
                                                                    new ModuleColumnResp(
                                                                            item.getId(),
                                                                            item.getColumnName(),
                                                                            item.getDisplayName()))
                                                    .collect(Collectors.toList())));

            moduleColumnPermission.stream()
                    .forEach(
                            ele ->
                                    detailReadFields.addAll(
                                            ele.getReadableFields().stream()
                                                    .map(
                                                            item ->
                                                                    new ModuleColumnResp(
                                                                            item.getId(),
                                                                            item.getColumnName(),
                                                                            item.getDisplayName()))
                                                    .collect(Collectors.toList())));

            if (!createFinded) {
                if (!CollectionUtils.isEmpty(detailCreateFields)) {
                    btnList.add(
                            FormButton.builder()
                                    .buttonKey(FormButtonEnum.CREATE.getKey())
                                    .buttonName(FormButtonEnum.CREATE.getName())
                                    .build());
                    createFinded = true;
                    log.debug("子模块名称:{},id:{}有创建权限", node.getModuleName(), node.getId());
                    // 有传入参数则不赋值，对应于创建接口调用
                    if (!StringUtils.hasText(btnType)) {
                        btnType = "create";
                    }
                }
            }

            // 编辑和查看二选一
            if (!editFinded) {
                if (!CollectionUtils.isEmpty(detailEditFields)) {
                    btnList.add(
                            FormButton.builder()
                                    .buttonKey(FormButtonEnum.EDITE.getKey())
                                    .buttonName(FormButtonEnum.EDITE.getName())
                                    .build());

                    // 有编辑权限，得去除查阅权限
                    log.debug("子模块名称:{},id:{}有编辑权限 ，去除查阅权限。", node.getModuleName(), node.getId());
                    boolean removeResult =
                            btnList.remove(
                                    FormButton.builder()
                                            .buttonKey(FormButtonEnum.VIEW.getKey())
                                            .buttonName(FormButtonEnum.VIEW.getName())
                                            .build());

                    if (removeResult) {
                        log.debug("查找到编辑权限之前，已经有查阅权限，去除成功。");
                    }

                    editFinded = true;
                    // 有传入参数则不赋值，对应于创建接口调用
                    if (!created) {
                        btnType = "edit";
                    }
                } else if (!CollectionUtils.isEmpty(detailReadFields)) {
                    btnList.add(
                            FormButton.builder()
                                    .buttonKey(FormButtonEnum.VIEW.getKey())
                                    .buttonName(FormButtonEnum.VIEW.getName())
                                    .build());
                    log.debug("子模块名称:{},id:{}有查阅权限", node.getModuleName(), node.getId());
                    // 有传入参数则不赋值，对应于创建接口调用
                    if (!created) {
                        btnType = "view";
                    }
                }
            }

            // 创建和编辑都找到了，则提前结束遍历
            if (createFinded && editFinded) {
                log.debug(
                        "模块Id:{}直到子模块名称:{},id:{}找到了创建和编辑或者查阅权限，提前终止遍历。",
                        parentModuleId,
                        node.getModuleName(),
                        node.getId());
                break;
            }
        }

        return btnType;
    }

    @Override
    public ApiResponse<RightInfoVO> getModuleApplyFieldList(
            Long subjectId, Long roleId, Long moduleId, String btn_type) {

        FormButtonEnum matchFormButtonEnum = FormButtonEnum.getFormButtonEnumByKey(btn_type);

        String model = "";
        if (FormButtonEnum.CREATE.equals(matchFormButtonEnum) || matchFormButtonEnum == null) {
            // 如果是创建，需要传值进权限获取方法，其他情况根据当前权限配置自动判断
            model = FormButtonEnum.CREATE.getKey();
        }

        ModuleFilterInfo moduleFilterInfo =
                filterModuleInfo(subjectId, roleId, moduleId, model, new ArrayList<>());
        if (moduleFilterInfo == null) {
            return ApiResponse.error(
                    ApiResponseCodeEnum.BAD_MENU_FIELD_APPLY_RIGHT_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MENU_FIELD_APPLY_RIGHT_ERROR.getMessage());
        }

        List<FormField> formFieldList = getFormFieldData(moduleFilterInfo, false);
        if (CollectionUtil.isEmpty(formFieldList)) {
            return ApiResponse.error(
                    ApiResponseCodeEnum.BAD_MENU_FIELD_APPLY_RIGHT_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MENU_FIELD_APPLY_RIGHT_ERROR.getMessage());
        }

        if (formFieldList.stream()
                .allMatch(
                        x ->
                                FIELD_THREE_PERMISSION_CODE.equals(x.getPermissionCode())
                                        || FIELD_ZERO_PERMISSION_CODE.equals(
                                                x.getPermissionCode()))) {
            return ApiResponse.error(
                    ApiResponseCodeEnum.BAD_MENU_FIELD_APPLY_RIGHT_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MENU_FIELD_APPLY_RIGHT_ERROR.getMessage());
        }

        return ApiResponse.success(
                RightInfoVO.builder()
                        .formField(formFieldList)
                        .btnList(moduleFilterInfo.getBtnList())
                        .interOperateNode(moduleFilterInfo.getInterOperateRights())
                        .build());
    }

    @Override
    public List<FormField> getFormFieldData(
            ModuleFilterInfo moduleFilterInfo, boolean isFilterUnWriteableField) {

        // 模块勾选的全部字段集合，方便后期前端低代码动态加载
        List<ModuleField> allFields = moduleFilterInfo.getAllFields();

        List<FormField> formFields = new ArrayList<>();

        Map<String, FormField> fieldMap = new HashMap<>();

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
            }

            if (isFilterUnWriteableField
                    && !FIELD_WRITE_PERMISSION_CODE.equals(allFieldItem.getPermissionCode())) {
                continue;
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
                            .relationBusinessNo(allFieldItem.getRelationBusinessNo())
                            .relationName(allFieldItem.getRelationName())
                            .fieldChainValues(allFieldItem.getFieldChainValues())
                            .build();

            formFields.add(formField);
            fieldMap.put(formField.getFieldName(), formField);
        }

        // 补充 主表的字段
        if (!fieldMap.containsKey(
                        moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                + DB_TABLE_FIELD_SEPARATOR
                                + DEFAULT_PRIMARY_KEY)
                || (!FIELD_WRITE_PERMISSION_CODE.equals(
                                fieldMap.get(
                                                moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                                        + DB_TABLE_FIELD_SEPARATOR
                                                        + DEFAULT_PRIMARY_KEY)
                                        .getPermissionCode())
                        && !FIELD_ONE_PERMISSION_CODE.equals(
                                fieldMap.get(
                                                moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                                        + DB_TABLE_FIELD_SEPARATOR
                                                        + DEFAULT_PRIMARY_KEY)
                                        .getPermissionCode()))) {
            if (fieldMap.get(
                            moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                    + DB_TABLE_FIELD_SEPARATOR
                                    + DEFAULT_PRIMARY_KEY)
                    != null) {
                fieldMap.get(
                                moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                        + DB_TABLE_FIELD_SEPARATOR
                                        + DEFAULT_PRIMARY_KEY)
                        .setPermissionCode(FIELD_THREE_PERMISSION_CODE);
            } else {
                FormField formField =
                        FormField.builder()
                                //
                                // .fieldName(allFieldItem.getFieldCode())
                                .fieldName(
                                        moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                                + DB_TABLE_FIELD_SEPARATOR
                                                + DEFAULT_PRIMARY_KEY) // 先统一返回表名加字段名处理重复字段
                                .fieldTitle(DEFAULT_PRIMARY_KEY)
                                .permissionCode(FIELD_THREE_PERMISSION_CODE)
                                .tableName(moduleFilterInfo.getModuleInfo().getPrimaryTable())
                                .build();

                formFields.add(formField);
                fieldMap.put(formField.getFieldName(), formField);
            }
        }

        if (org.apache.commons.lang3.StringUtils.isNotEmpty(
                        moduleFilterInfo.getModuleInfo().getRelateSearchField())
                && (!fieldMap.containsKey(
                                moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                        + DB_TABLE_FIELD_SEPARATOR
                                        + ToolUtils.snakeToCamelCase(
                                                moduleFilterInfo
                                                        .getModuleInfo()
                                                        .getRelateSearchField()))
                        || (!FIELD_WRITE_PERMISSION_CODE.equals(
                                        fieldMap.get(
                                                        moduleFilterInfo
                                                                        .getModuleInfo()
                                                                        .getPrimaryTable()
                                                                + DB_TABLE_FIELD_SEPARATOR
                                                                + ToolUtils.snakeToCamelCase(
                                                                        moduleFilterInfo
                                                                                .getModuleInfo()
                                                                                .getRelateSearchField()))
                                                .getPermissionCode())
                                && !FIELD_ONE_PERMISSION_CODE.equals(
                                        fieldMap.get(
                                                        moduleFilterInfo
                                                                        .getModuleInfo()
                                                                        .getPrimaryTable()
                                                                + DB_TABLE_FIELD_SEPARATOR
                                                                + ToolUtils.snakeToCamelCase(
                                                                        moduleFilterInfo
                                                                                .getModuleInfo()
                                                                                .getRelateSearchField()))
                                                .getPermissionCode())))) {

            if (fieldMap.get(
                            moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                    + DB_TABLE_FIELD_SEPARATOR
                                    + ToolUtils.snakeToCamelCase(
                                            moduleFilterInfo
                                                    .getModuleInfo()
                                                    .getRelateSearchField()))
                    != null) {
                fieldMap.get(
                                moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                        + DB_TABLE_FIELD_SEPARATOR
                                        + ToolUtils.snakeToCamelCase(
                                                moduleFilterInfo
                                                        .getModuleInfo()
                                                        .getRelateSearchField()))
                        .setPermissionCode(FIELD_THREE_PERMISSION_CODE);
            } else {
                FormField formField =
                        FormField.builder()
                                //
                                // .fieldName(allFieldItem.getFieldCode())
                                .fieldName(
                                        moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                                + DB_TABLE_FIELD_SEPARATOR
                                                + ToolUtils.snakeToCamelCase(
                                                        moduleFilterInfo
                                                                .getModuleInfo()
                                                                .getRelateSearchField())) // 先统一返回表名加字段名处理重复字段
                                .fieldTitle(moduleFilterInfo.getModuleInfo().getRelateSearchField())
                                .permissionCode(FIELD_THREE_PERMISSION_CODE)
                                .tableName(moduleFilterInfo.getModuleInfo().getPrimaryTable())
                                .build();
                formFields.add(formField);
                fieldMap.put(formField.getFieldName(), formField);
            }
        }

        if (CollectionUtil.isNotEmpty(moduleFilterInfo.getModuleTableList())) {
            for (ModuleTable moduleTable : moduleFilterInfo.getModuleTableList()) {
                if (!fieldMap.containsKey(
                                moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                        + DB_TABLE_FIELD_SEPARATOR
                                        + ToolUtils.snakeToCamelCase(
                                                moduleTable.getJoinLeftField()))
                        || (!FIELD_WRITE_PERMISSION_CODE.equals(
                                        fieldMap.get(
                                                        moduleFilterInfo
                                                                        .getModuleInfo()
                                                                        .getPrimaryTable()
                                                                + DB_TABLE_FIELD_SEPARATOR
                                                                + ToolUtils.snakeToCamelCase(
                                                                        moduleTable
                                                                                .getJoinLeftField()))
                                                .getPermissionCode())
                                && !FIELD_ONE_PERMISSION_CODE.equals(
                                        fieldMap.get(
                                                        moduleFilterInfo
                                                                        .getModuleInfo()
                                                                        .getPrimaryTable()
                                                                + DB_TABLE_FIELD_SEPARATOR
                                                                + ToolUtils.snakeToCamelCase(
                                                                        moduleTable
                                                                                .getJoinLeftField()))
                                                .getPermissionCode())
                                && !FIELD_THREE_PERMISSION_CODE.equals(
                                        fieldMap.get(
                                                        moduleFilterInfo
                                                                        .getModuleInfo()
                                                                        .getPrimaryTable()
                                                                + DB_TABLE_FIELD_SEPARATOR
                                                                + ToolUtils.snakeToCamelCase(
                                                                        moduleTable
                                                                                .getJoinLeftField()))
                                                .getPermissionCode()))) {
                    if (fieldMap.get(
                                    moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                            + DB_TABLE_FIELD_SEPARATOR
                                            + ToolUtils.snakeToCamelCase(
                                                    moduleTable.getJoinLeftField()))
                            != null) {
                        fieldMap.get(
                                        moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                                + DB_TABLE_FIELD_SEPARATOR
                                                + ToolUtils.snakeToCamelCase(
                                                        moduleTable.getJoinLeftField()))
                                .setPermissionCode(FIELD_THREE_PERMISSION_CODE);
                    } else {
                        FormField formField =
                                FormField.builder()
                                        //
                                        // .fieldName(allFieldItem.getFieldCode())
                                        .fieldName(
                                                moduleFilterInfo.getModuleInfo().getPrimaryTable()
                                                        + DB_TABLE_FIELD_SEPARATOR
                                                        + ToolUtils.snakeToCamelCase(
                                                                moduleTable
                                                                        .getJoinLeftField())) // 先统一返回表名加字段名处理重复字段
                                        .fieldTitle(moduleTable.getJoinLeftField())
                                        .permissionCode(FIELD_THREE_PERMISSION_CODE)
                                        .tableName(
                                                moduleFilterInfo.getModuleInfo().getPrimaryTable())
                                        .build();
                        formFields.add(formField);
                        fieldMap.put(formField.getFieldName(), formField);
                    }
                }

                if (!fieldMap.containsKey(
                                moduleTable.getTableName()
                                        + DB_TABLE_FIELD_SEPARATOR
                                        + ToolUtils.snakeToCamelCase(
                                                moduleTable.getJoinRightField()))
                        || (!FIELD_WRITE_PERMISSION_CODE.equals(
                                        fieldMap.get(
                                                        moduleTable.getTableName()
                                                                + DB_TABLE_FIELD_SEPARATOR
                                                                + ToolUtils.snakeToCamelCase(
                                                                        moduleTable
                                                                                .getJoinRightField()))
                                                .getPermissionCode())
                                && !FIELD_ONE_PERMISSION_CODE.equals(
                                        fieldMap.get(
                                                        moduleTable.getTableName()
                                                                + DB_TABLE_FIELD_SEPARATOR
                                                                + ToolUtils.snakeToCamelCase(
                                                                        moduleTable
                                                                                .getJoinRightField()))
                                                .getPermissionCode()))) {
                    if (fieldMap.get(
                                    moduleTable.getTableName()
                                            + DB_TABLE_FIELD_SEPARATOR
                                            + ToolUtils.snakeToCamelCase(
                                                    moduleTable.getJoinRightField()))
                            != null) {
                        fieldMap.get(
                                        moduleTable.getTableName()
                                                + DB_TABLE_FIELD_SEPARATOR
                                                + ToolUtils.snakeToCamelCase(
                                                        moduleTable.getJoinRightField()))
                                .setPermissionCode(FIELD_THREE_PERMISSION_CODE);
                    } else {
                        FormField formField =
                                FormField.builder()
                                        //
                                        // .fieldName(allFieldItem.getFieldCode())
                                        .fieldName(
                                                moduleTable.getTableName()
                                                        + DB_TABLE_FIELD_SEPARATOR
                                                        + ToolUtils.snakeToCamelCase(
                                                                moduleTable
                                                                        .getJoinRightField())) // 先统一返回表名加字段名处理重复字段
                                        .fieldTitle(moduleTable.getJoinRightField())
                                        .permissionCode(FIELD_THREE_PERMISSION_CODE)
                                        .tableName(moduleTable.getTableName())
                                        .build();
                        formFields.add(formField);
                        fieldMap.put(formField.getFieldName(), formField);
                    }
                }

                if (fieldMap.containsKey(
                                moduleTable.getTableName()
                                        + DB_TABLE_FIELD_SEPARATOR
                                        + DEFAULT_PRIMARY_KEY)
                        || (!FIELD_WRITE_PERMISSION_CODE.equals(
                                        fieldMap.get(
                                                        moduleTable.getTableName()
                                                                + DB_TABLE_FIELD_SEPARATOR
                                                                + DEFAULT_PRIMARY_KEY)
                                                .getPermissionCode())
                                && !FIELD_ONE_PERMISSION_CODE.equals(
                                        fieldMap.get(
                                                        moduleTable.getTableName()
                                                                + DB_TABLE_FIELD_SEPARATOR
                                                                + DEFAULT_PRIMARY_KEY)
                                                .getPermissionCode()))) {
                    if (fieldMap.get(
                                    moduleTable.getTableName()
                                            + DB_TABLE_FIELD_SEPARATOR
                                            + DEFAULT_PRIMARY_KEY)
                            != null) {
                        fieldMap.get(
                                        moduleTable.getTableName()
                                                + DB_TABLE_FIELD_SEPARATOR
                                                + DEFAULT_PRIMARY_KEY)
                                .setPermissionCode(FIELD_THREE_PERMISSION_CODE);
                    } else {
                        FormField formField =
                                FormField.builder()
                                        //
                                        // .fieldName(allFieldItem.getFieldCode())
                                        .fieldName(
                                                moduleTable.getTableName()
                                                        + DB_TABLE_FIELD_SEPARATOR
                                                        + DEFAULT_PRIMARY_KEY) // 先统一返回表名加字段名处理重复字段
                                        .fieldTitle(DEFAULT_PRIMARY_KEY)
                                        .permissionCode(FIELD_THREE_PERMISSION_CODE)
                                        .tableName(moduleTable.getTableName())
                                        .build();
                        formFields.add(formField);
                        fieldMap.put(formField.getFieldName(), formField);
                    }
                }
            }
        }

        return formFields;
    }

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

    private record RightNode(String fieldCode, String fieldName) {}
}
