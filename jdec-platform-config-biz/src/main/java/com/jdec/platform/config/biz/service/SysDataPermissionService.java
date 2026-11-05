package com.jdec.platform.config.biz.service;

import static org.jooq.impl.DSL.*;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.SysDataPermissionApi;
import com.jdec.platform.config.api.dto.request.DataPermissionQueryItem;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.config.biz.entity.SysDataPermission;
import com.jdec.platform.config.biz.entity.SysField;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleField;
import com.jdec.platform.config.biz.entity.SysProjectSubject;
import com.jdec.platform.config.biz.mapper.SysDataPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysProjectSubjectMapper;
import com.jdec.platform.shared.audit.annotation.DataAudit;
import com.jdec.platform.shared.audit.enums.OperationType;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import com.jdec.platform.shared.enums.CommonStatusEnum;
import com.jdec.platform.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 数据权限类型 Service 实现 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysDataPermissionService implements SysDataPermissionApi {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 业务数据为空时的统一占位值 */
    private static final String NULL_VALUE = "Null";

    /** 是否来自词云：是（sys_field.relation_business_no 有值） */
    private static final Integer CLOUD_YES = 1;

    /** 是否来自词云：否 */
    private static final Integer CLOUD_NO = 0;

    /** 词云业务数据需逐层剥离的内部字段 */
    private static final Set<String> WORD_CLOUD_REMOVED_FIELDS =
            Set.of("id", "pid", "relation_no", "relation_id", "word_id");

    private final SysDataPermissionMapper sysDataPermissionMapper;
    private final SysProjectSubjectMapper sysProjectSubjectMapper;
    private final SysModuleMapper sysModuleMapper;
    private final SysModuleFieldMapper sysModuleFieldMapper;
    private final SysFieldMapper sysFieldMapper;
    private final DataSourceResolver dataSourceResolver;

    @Override
    @DataAudit(
            module = "系统设置",
            subModule = "数据权限配置",
            operation = OperationType.UPDATE,
            tableName = "sys_data_permission",
            dataIdField = "#req.id")
    public SysRightResp saveDataPermission(SysDataPermissionSaveReq req) {
        SysDataPermission entity;
        if (req.getId() == null) {
            // 校验编码唯一性
            boolean exists =
                    sysDataPermissionMapper.exists(
                            Wrappers.<SysDataPermission>lambdaQuery()
                                    .eq(SysDataPermission::getCode, req.getCode())
                                    .eq(SysDataPermission::getSubjectId, AppContext.getSubjectId())
                                    .eq(
                                            SysDataPermission::getProjectNo,
                                            AppContext.getProjectNo()));
            if (exists) {
                throw new BusinessException("类型编码已存在");
            }
            // 新增
            entity = new SysDataPermission();
            BeanUtils.copyProperties(req, entity);
            entity.setSubjectId(AppContext.getSubjectId());
            entity.setProjectNo(AppContext.getProjectNo());
            sysDataPermissionMapper.insert(entity);

            // 设置id到请求对象，用于审计切面重新解析dataId
            req.setId(entity.getId());
        } else {
            entity = sysDataPermissionMapper.selectById(req.getId());
            if (entity == null) {
                throw new BusinessException("数据权限类型不存在");
            }
            // 校验编码唯一性（排除自己）
            if (!entity.getCode().equals(req.getCode())) {
                boolean exists =
                        sysDataPermissionMapper.exists(
                                Wrappers.<SysDataPermission>lambdaQuery()
                                        .eq(SysDataPermission::getCode, req.getCode())
                                        .ne(SysDataPermission::getId, req.getId())
                                        .eq(
                                                SysDataPermission::getSubjectId,
                                                AppContext.getSubjectId())
                                        .eq(
                                                SysDataPermission::getProjectNo,
                                                AppContext.getProjectNo()));
                if (exists) {
                    throw new BusinessException("类型编码已存在");
                }
            }
            entity.setCode(req.getCode());
            entity.setName(req.getName());
            entity.setDescription(req.getDescription());
            entity.setSort(req.getSort());
            entity.setStatus(req.getStatus());
            sysDataPermissionMapper.updateById(entity);
        }

        // 构建返回对象
        SysRightResp resp = new SysRightResp();
        resp.setId(entity.getId());
        resp.setRightName(entity.getName());
        resp.setRightSlug(entity.getCode());
        resp.setNodeType(1);
        resp.setDescription(entity.getDescription());
        resp.setParentRightName(null);
        resp.setParentRightSlug(null);
        resp.setType(null);
        return resp;
    }

    @Override
    public List<SysDataPermissionResp> listDataPermission(QuerySysDataPermissionReq req) {
        // 1. 查询当前主体和项目编码下的数据权限节点
        List<SysDataPermission> list =
                sysDataPermissionMapper.selectList(
                        new LambdaQueryWrapper<SysDataPermission>()
                                .eq(SysDataPermission::getProjectNo, AppContext.getProjectNo())
                                .eq(SysDataPermission::getSubjectId, AppContext.getSubjectId())
                                .like(
                                        StringUtils.hasText(req.getName()),
                                        SysDataPermission::getName,
                                        req.getName())
                                .like(
                                        StringUtils.hasText(req.getCode()),
                                        SysDataPermission::getCode,
                                        req.getCode())
                                .eq(SysDataPermission::getStatus, CommonStatusEnum.ENABLE.getCode())
                                .orderByAsc(SysDataPermission::getSort)
                                .orderByAsc(SysDataPermission::getId));

        if (list.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 根据项目编码查询项目主体列表
        List<SysProjectSubject> subjects =
                sysProjectSubjectMapper.selectList(
                        new LambdaQueryWrapper<SysProjectSubject>()
                                .eq(SysProjectSubject::getProjectNo, AppContext.getProjectNo()));

        // 3. 解析每个权限节点关联的主体（模块数据来源主体与项目主体取交集）
        Map<String, List<SysProjectSubject>> codeSubjectMap = resolveNodeSubjects(list, subjects);

        // 4. 解析每个权限节点的业务类型（按「表名*字段名」匹配 sys_field）
        Map<String, SysField> codeFieldMap = resolveBusinessTypes(list);

        // 5. 按节点关联的主体转换为查询参数（编码形如「表名*字段名」）
        List<DataPermissionQueryItem> queryItems = new ArrayList<>();
        for (SysDataPermission permission : list) {
            List<SysProjectSubject> nodeSubjects = codeSubjectMap.get(permission.getCode());
            String[] parts = splitCode(permission.getCode());
            if (CollUtil.isEmpty(nodeSubjects) || parts == null) {
                continue;
            }
            DataPermissionQueryItem item = new DataPermissionQueryItem();
            item.setTableName(parts[0]);
            item.setColumn(parts[1]);
            SysField fieldConfig = codeFieldMap.get(permission.getCode());
            item.setCloud(
                    fieldConfig != null && StringUtils.hasText(fieldConfig.getRelationBusinessNo())
                            ? CLOUD_YES
                            : CLOUD_NO);
            item.setSubjectIds(
                    nodeSubjects.stream()
                            .map(SysProjectSubject::getSubjectId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .collect(Collectors.toList()));
            queryItems.add(item);
        }

        // 6. 直连项目编码对应的数据源查询，按 主体ID -> 权限编码 缓存业务数据
        Map<Long, Map<String, List<Object>>> subjectBizDataMap =
                CollUtil.isEmpty(queryItems)
                        ? new HashMap<>()
                        : queryBizData(AppContext.getProjectNo(), queryItems);

        // 7. 层层封装返回：权限节点 -> 主体列表 -> 主体业务数据
        return list.stream()
                .map(
                        type -> {
                            SysDataPermissionResp resp = new SysDataPermissionResp();
                            resp.setId(type.getId());
                            resp.setCode(type.getCode());
                            resp.setName(type.getName());
                            resp.setDescription(type.getDescription());
                            resp.setSort(type.getSort());
                            resp.setStatus(type.getStatus());
                            SysField fieldConfig = codeFieldMap.get(type.getCode());
                            resp.setBusinessTypeCode(
                                    fieldConfig != null ? fieldConfig.getBusinessTypeCode() : null);
                            resp.setBusinessTypeName(
                                    fieldConfig != null ? fieldConfig.getBusinessTypeName() : null);
                            // 是否来自词云：按权限编码「表名*字段名」在 sys_field 的关联关系业务编号判定
                            resp.setCloud(
                                    fieldConfig != null
                                                    && StringUtils.hasText(
                                                            fieldConfig.getRelationBusinessNo())
                                            ? CLOUD_YES
                                            : CLOUD_NO);
                            resp.setSubjectInfos(
                                    CollUtil.emptyIfNull(codeSubjectMap.get(type.getCode()))
                                            .stream()
                                            .map(
                                                    subject -> {
                                                        SysDataPermissionResp.SubjectInfo info =
                                                                new SysDataPermissionResp
                                                                        .SubjectInfo();
                                                        info.setSubjectId(subject.getSubjectId());
                                                        info.setSubjectName(
                                                                subject.getSubjectName());
                                                        info.setBizNames(
                                                                subjectBizDataMap
                                                                        .getOrDefault(
                                                                                subject
                                                                                        .getSubjectId(),
                                                                                Collections
                                                                                        .emptyMap())
                                                                        .getOrDefault(
                                                                                type.getCode(),
                                                                                new ArrayList<>()));
                                                        return info;
                                                    })
                                            .collect(Collectors.toList()));
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    /**
     * 解析每个数据权限节点关联的主体列表。
     *
     * <p>按权限编码「表名*字段名」在 {@code sys_module_field} 中匹配模块ID，再取 {@code sys_module} 的 {@code
     * source_subjects}（为空时回退 {@code subject_id}），各模块主体合并去重后与当前项目主体取交集， 按项目主体顺序返回。
     */
    private Map<String, List<SysProjectSubject>> resolveNodeSubjects(
            List<SysDataPermission> permissions, List<SysProjectSubject> projectSubjects) {
        Map<String, List<SysProjectSubject>> codeSubjectMap = new HashMap<>();
        if (CollUtil.isEmpty(projectSubjects)) {
            return codeSubjectMap;
        }

        // 权限编码 -> [表名, 字段名]
        Map<String, String[]> codePartsMap = new LinkedHashMap<>();
        for (SysDataPermission permission : permissions) {
            String[] parts = splitCode(permission.getCode());
            if (parts != null) {
                codePartsMap.put(permission.getCode(), parts);
            }
        }
        if (codePartsMap.isEmpty()) {
            return codeSubjectMap;
        }

        // 1. 按表名 + 字段名查询模块字段，得到 权限编码 -> 模块ID集合
        Set<String> tableNames =
                codePartsMap.values().stream().map(parts -> parts[0]).collect(Collectors.toSet());
        Set<String> columnNames =
                codePartsMap.values().stream().map(parts -> parts[1]).collect(Collectors.toSet());
        List<SysModuleField> fields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .in(SysModuleField::getTableName, tableNames)
                                .in(SysModuleField::getColumnName, columnNames));
        Map<String, Set<Long>> codeModuleIdsMap = new HashMap<>();
        for (SysModuleField field : fields) {
            if (field.getModuleId() == null) {
                continue;
            }
            String code = field.getTableName() + "*" + field.getColumnName();
            if (codePartsMap.containsKey(code)) {
                codeModuleIdsMap
                        .computeIfAbsent(code, key -> new LinkedHashSet<>())
                        .add(field.getModuleId());
            }
        }
        if (codeModuleIdsMap.isEmpty()) {
            return codeSubjectMap;
        }

        // 2. 查询模块，取得每个模块的数据来源主体
        Set<Long> moduleIds =
                codeModuleIdsMap.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        Map<Long, SysModule> moduleMap =
                sysModuleMapper
                        .selectList(
                                Wrappers.<SysModule>lambdaQuery()
                                        .in(SysModule::getId, moduleIds)
                                        .eq(SysModule::getProjectNo, AppContext.getProjectNo()))
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        SysModule::getId,
                                        module -> module,
                                        (first, second) -> first));

        // 3. 合并各模块主体去重后，与项目主体取交集
        for (Map.Entry<String, String[]> entry : codePartsMap.entrySet()) {
            Set<Long> subjectIds = new LinkedHashSet<>();
            for (Long moduleId : codeModuleIdsMap.getOrDefault(entry.getKey(), Set.of())) {
                SysModule module = moduleMap.get(moduleId);
                if (module == null) {
                    continue;
                }
                if (CollUtil.isNotEmpty(module.getSourceSubjects())) {
                    module.getSourceSubjects().stream()
                            .filter(Objects::nonNull)
                            .forEach(subjectIds::add);
                } else if (module.getSubjectId() != null) {
                    subjectIds.add(module.getSubjectId());
                }
            }
            codeSubjectMap.put(
                    entry.getKey(),
                    projectSubjects.stream()
                            .filter(
                                    subject ->
                                            subject.getSubjectId() != null
                                                    && subjectIds.contains(subject.getSubjectId()))
                            .collect(Collectors.toList()));
        }
        return codeSubjectMap;
    }

    /**
     * 按权限编码「表名*字段名」批量查询 {@code sys_field}，取得字段配置的业务类型与关联关系业务编号（用于词云标识）。
     *
     * <p>返回 权限编码 -> 字段配置（同键保留首条），编码非法或无匹配配置时返回空。
     */
    private Map<String, SysField> resolveBusinessTypes(List<SysDataPermission> permissions) {
        Map<String, SysField> codeFieldMap = new HashMap<>();

        // 权限编码 -> [表名, 字段名]
        Map<String, String[]> codePartsMap = new LinkedHashMap<>();
        for (SysDataPermission permission : permissions) {
            String[] parts = splitCode(permission.getCode());
            if (parts != null) {
                codePartsMap.put(permission.getCode(), parts);
            }
        }
        if (codePartsMap.isEmpty()) {
            return codeFieldMap;
        }

        Set<String> tableNames =
                codePartsMap.values().stream().map(parts -> parts[0]).collect(Collectors.toSet());
        Set<String> columnNames =
                codePartsMap.values().stream().map(parts -> parts[1]).collect(Collectors.toSet());
        List<SysField> fields =
                sysFieldMapper.selectList(
                        Wrappers.<SysField>lambdaQuery()
                                .in(SysField::getTableName, tableNames)
                                .in(SysField::getColumnName, columnNames)
                                .eq(SysField::getDeleted, 0)
                                .eq(SysField::getProjectNo, AppContext.getProjectNo())
                                .eq(SysField::getSubjectId, AppContext.getSubjectId()));

        for (SysField field : fields) {
            if (field.getTableName() == null || field.getColumnName() == null) {
                continue;
            }
            String code = field.getTableName() + "*" + field.getColumnName();
            if (codePartsMap.containsKey(code)) {
                codeFieldMap.putIfAbsent(code, field);
            }
        }
        return codeFieldMap;
    }

    /** 拆解权限编码「表名*字段名」，非法编码返回 null */
    private String[] splitCode(String code) {
        if (!StringUtils.hasText(code) || !code.contains("*")) {
            return null;
        }
        String[] parts = code.split("\\*");
        return parts.length == 2 && StringUtils.hasText(parts[0]) && StringUtils.hasText(parts[1])
                ? parts
                : null;
    }

    /**
     * 直连项目编码对应的数据源，查询各权限编码下的业务数据。
     *
     * <p>权限编码形如「表名*字段名」，逐项拼接 SQL： {@code SELECT DISTINCT subject_id, 字段名 FROM 表名 WHERE subject_id
     * IN (主体ID列表)}， 结果按 主体ID -> 权限编码 归集，保持字段原始类型返回：标量原样返回，JSON 字段返回 JSON 结构而非 JSON 文本，空值记为 "Null"。
     * 字段值存数组时按元素打散拆分，每项元素各算一条业务数据，同一主体下重复的数据只保留一条 （去重口径见 {@link #dedupKey(Object)}）；
     * 单个查询项查询失败时仅记日志，不影响权限节点列表查询。
     */
    private Map<Long, Map<String, List<Object>>> queryBizData(
            String projectNo, List<DataPermissionQueryItem> queryItems) {
        Map<Long, Map<String, List<Object>>> subjectBizDataMap = new HashMap<>();
        if (CollUtil.isEmpty(queryItems)) {
            return subjectBizDataMap;
        }

        DSLContext dsl;
        try {
            dsl = dataSourceResolver.getDSLContextByProjectNo(projectNo);
        } catch (Exception e) {
            log.error("项目 {} 数据源解析失败，无法查询业务数据", projectNo, e);
            return subjectBizDataMap;
        }

        Field<String> subjectField = field(name("subject_id"), String.class);
        // 去重键集合：主体ID#权限编码 -> 已收集业务数据的去重键
        Map<String, Set<Object>> seenBizNameKeys = new HashMap<>();
        for (DataPermissionQueryItem item : queryItems) {
            List<String> subjectIds =
                    CollUtil.emptyIfNull(item.getSubjectIds()).stream()
                            .filter(Objects::nonNull)
                            .map(String::valueOf)
                            .collect(Collectors.toList());
            if (subjectIds.isEmpty()
                    || !StringUtils.hasText(item.getTableName())
                    || !StringUtils.hasText(item.getColumn())) {
                continue;
            }

            String dataPermissionCode = item.getTableName() + "*" + item.getColumn();
            try {
                // 按 JDBC 原始类型读取，前端拿到与业务字段一致的类型（数字不转字符串）
                Field<Object> valueField = field(name(item.getColumn()), Object.class);
                var records =
                        dsl.selectDistinct(subjectField, valueField)
                                .from(table(name(item.getTableName())))
                                .where(subjectField.in(subjectIds))
                                .fetch();

                for (Record record : records) {
                    Long subjectId = parseSubjectId(record.get(subjectField));
                    if (subjectId == null) {
                        continue;
                    }
                    Object value = record.get(valueField);
                    List<Object> bizNames =
                            subjectBizDataMap
                                    .computeIfAbsent(subjectId, key -> new HashMap<>())
                                    .computeIfAbsent(dataPermissionCode, key -> new ArrayList<>());
                    Set<Object> seenKeys =
                            seenBizNameKeys.computeIfAbsent(
                                    subjectId + "#" + dataPermissionCode, key -> new HashSet<>());
                    // 数组字段按元素拆分，同一业务数据只保留首次出现的展示值
                    boolean cloud = CLOUD_YES.equals(item.getCloud());
                    for (Map.Entry<Object, Object> entry :
                            flattenBizNames(value, cloud).entrySet()) {
                        if (seenKeys.add(entry.getKey())) {
                            bizNames.add(entry.getValue());
                        }
                    }
                }
            } catch (Exception e) {
                log.error(
                        "查询业务数据失败: projectNo={}, table={}, column={}",
                        projectNo,
                        item.getTableName(),
                        item.getColumn(),
                        e);
            }
        }
        return subjectBizDataMap;
    }

    /**
     * 将字段值打散拆分，返回「去重键 -> 展示值」映射，保持元素出现顺序。
     *
     * <p>数组按元素逐个展开，非数组按单值返回；展示值尽量保留业务字段原始类型，JSON 文本与 jOOQ JSON/JSONB 对象解析为 {@code Map}/{@code List}
     * 后返回，前端拿到的是 JSON 结构而不是 JSON 文本，标量（字符串、数字、布尔）原样返回； 字段值为空时保留占位 {@link #NULL_VALUE}，数组内的空元素直接忽略。
     *
     * <p>词云来源的数据（{@code cloud=true}）会先逐层剥离内部字段（{@link #WORD_CLOUD_REMOVED_FIELDS}）再按剥离后的结构去重。
     */
    private Map<Object, Object> flattenBizNames(Object value, boolean cloud) {
        Map<Object, Object> bizNames = new LinkedHashMap<>();
        if (value == null) {
            bizNames.put(NULL_VALUE, NULL_VALUE);
            return bizNames;
        }
        for (Object element : toElements(value)) {
            if (element != null) {
                Object displayValue = toDisplayValue(element, cloud);
                bizNames.putIfAbsent(
                        dedupKey(displayValue), displayValue == null ? NULL_VALUE : displayValue);
            }
        }
        return bizNames;
    }

    /**
     * 业务数据的展示值：尽量保留业务字段的原始类型。
     *
     * <p>jOOQ JSON/JSONB 包装类型先取出 JSON 文本，再按 JSON 结构解析为 {@code Map}/{@code List}；字符串、数字、布尔等
     * 标量原样返回，解析失败时按原始文本返回。词云来源的数据会先剥离内部字段。
     */
    private Object toDisplayValue(Object element, boolean cloud) {
        Object value = unwrapJson(element);
        if (value instanceof CharSequence text) {
            String content = text.toString().trim();
            if (isJsonContainer(content)) {
                try {
                    return stripWordCloudFields(
                            OBJECT_MAPPER.readValue(content, Object.class), cloud);
                } catch (Exception e) {
                    log.debug("字段值不是合法的 JSON，按单值处理: {}", content);
                    return value;
                }
            }
        }
        return stripWordCloudFields(value, cloud);
    }

    /**
     * 词云来源的业务数据，逐层匹配并剥离内部元数据字段（{@code id/pid/relation_no/relation_id/word_id}）。
     *
     * <p>对 JSON 对象按 Key 递归删除这些字段，JSON 数组递归处理每个元素，非 JSON 结构原样返回。剥离后在 {@link #flattenBizNames(Object,
     * boolean)} 中按剥离后的结构去重，避免仅因这些元数据不同而判定为不同业务数据；非词云来源直接原样返回。
     */
    private Object stripWordCloudFields(Object value, boolean cloud) {
        if (!cloud || value == null) {
            return value;
        }
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = (Map<String, Object>) rawMap;
            map.keySet().removeIf(WORD_CLOUD_REMOVED_FIELDS::contains);
            for (Object child : map.values()) {
                stripWordCloudFields(child, true);
            }
        } else if (value instanceof List<?> list) {
            for (Object child : list) {
                stripWordCloudFields(child, true);
            }
        }
        return value;
    }

    /** 判断文本是否形如 JSON 对象或数组 */
    private boolean isJsonContainer(String content) {
        return (content.startsWith("{") && content.endsWith("}"))
                || (content.startsWith("[") && content.endsWith("]"));
    }

    /** 解包 jOOQ 的 JSON/JSONB 包装类型，其余类型原样返回 */
    private Object unwrapJson(Object value) {
        if (value instanceof JSON jooqJson) {
            return jooqJson.data();
        }
        if (value instanceof JSONB jooqJsonb) {
            return jooqJsonb.data();
        }
        return value;
    }

    /**
     * 业务数据的去重键：决定两条拆分结果是否算同一条。
     *
     * <p>数字按数值比较（{@code 1} 与 {@code 1.0} 视为同一值）；对象/数组按 JSON 结构比较，忽略对象键顺序；
     * 字符串、布尔等按原值严格比较（不忽略大小写与首尾空格）。
     */
    private Object dedupKey(Object element) {
        if (element instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).stripTrailingZeros();
            } catch (NumberFormatException e) {
                return number.toString();
            }
        }
        if (element instanceof Map<?, ?> || element instanceof Collection<?>) {
            try {
                return OBJECT_MAPPER.valueToTree(element);
            } catch (Exception e) {
                return String.valueOf(element);
            }
        }
        return element;
    }

    /** 识别并展开数组值：jOOQ JSON、集合、Java 数组、JSON 数组文本，其余按单值返回 */
    private List<Object> toElements(Object value) {
        value = unwrapJson(value);
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value instanceof Object[] array) {
            return Arrays.asList(array);
        }
        if (value instanceof CharSequence text) {
            String content = text.toString().trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                try {
                    return OBJECT_MAPPER.readValue(content, new TypeReference<List<Object>>() {});
                } catch (Exception e) {
                    log.debug("字段值不是合法的 JSON 数组，按单值处理: {}", content);
                }
            }
        }
        return Collections.singletonList(value);
    }

    /** 将查询结果中的主体ID转换为 Long，非法值返回 null */
    private Long parseSubjectId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            log.warn("主体ID非法: {}", value);
            return null;
        }
    }
}
