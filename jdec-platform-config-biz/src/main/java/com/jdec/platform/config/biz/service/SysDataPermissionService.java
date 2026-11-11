package com.jdec.platform.config.biz.service;

import static org.jooq.impl.DSL.*;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.SysDataPermissionApi;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.config.biz.entity.SysDataPermission;
import com.jdec.platform.config.biz.entity.SysField;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleField;
import com.jdec.platform.config.biz.entity.SysProjectSubject;
import com.jdec.platform.config.biz.entity.SysTableRelation;
import com.jdec.platform.config.biz.mapper.SysDataPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysProjectSubjectMapper;
import com.jdec.platform.config.biz.mapper.SysTableRelationMapper;
import com.jdec.platform.shared.audit.annotation.DataAudit;
import com.jdec.platform.shared.audit.enums.OperationType;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import com.jdec.platform.shared.enums.CommonStatusEnum;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.utils.JsonValueUtils;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
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
    private final SysTableRelationMapper sysTableRelationMapper;
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

        // 3. 解析每个权限节点的业务类型（按「表名*字段名」匹配 sys_field，用于词云标识与业务类型）
        Map<String, SysField> codeFieldMap = resolveBusinessTypes(list);

        // 4. 解析每个权限节点的模块信息：所属模块名称、顶层模块主表、表关联下钻路径、关联主体
        Map<String, CodeMeta> codeMetaMap = resolveCodeMeta(list, subjects, codeFieldMap);

        // 5. 直连项目编码对应的数据源，按「主体ID -> 权限编码」归集业务数据（含表关联逐层下钻）
        List<CodeMeta> queryMetas = new ArrayList<>(codeMetaMap.values());
        Map<Long, Map<String, List<Object>>> subjectBizDataMap =
                CollUtil.isEmpty(queryMetas)
                        ? new HashMap<>()
                        : queryBizData(AppContext.getProjectNo(), queryMetas);

        // 6. 层层封装返回：权限节点 -> 模块名称 / 主体列表 -> 主体业务数据
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

                            // 模块名称：由 sys_module_field 命中的模块经 sys_module 解析得到
                            CodeMeta meta = codeMetaMap.get(type.getCode());
                            resp.setModuleName(meta != null ? meta.getModuleName() : null);
                            if (meta != null) {
                                List<SysProjectSubject> nodeSubjects =
                                        subjects.stream()
                                                .filter(
                                                        s ->
                                                                meta.getSubjectIds()
                                                                        .contains(s.getSubjectId()))
                                                .collect(Collectors.toList());
                                resp.setSubjectInfos(
                                        nodeSubjects.stream()
                                                .map(
                                                        subject -> {
                                                            SysDataPermissionResp.SubjectInfo info =
                                                                    new SysDataPermissionResp
                                                                            .SubjectInfo();
                                                            info.setSubjectId(
                                                                    subject.getSubjectId());
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
                            }
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    /**
     * 解析每个数据权限节点的模块上下文（{@link CodeMeta}）。
     *
     * <p>解析流程：权限编码「表名*字段名」-> {@code sys_module_field}（表名+字段名匹配）-> 模块ID -> {@code sys_module} 沿
     * {@code parent_id} 父链找到<b>顶层模块</b>，取顶层模块的 {@code primaryTable}（主表）作为业务数据下钻的起点； 再结合本项目 {@code
     * sys_table_relation} 全局表关联关系，用 BFS 计算出从主表到权限节点所在表的<b>逐层下钻路径</b>。 同时解析权限节点关联的主体（模块 {@code
     * source_subjects} 为空时回退 {@code subject_id}，与项目主体取交集）。
     *
     * <p>编码非法、未匹配到模块、或匹配模块无主表时，该权限节点不进入返回结果（无模块名称与业务数据可展示）。
     */
    private Map<String, CodeMeta> resolveCodeMeta(
            List<SysDataPermission> permissions,
            List<SysProjectSubject> projectSubjects,
            Map<String, SysField> codeFieldMap) {
        Map<String, CodeMeta> result = new LinkedHashMap<>();
        if (CollUtil.isEmpty(projectSubjects)) {
            return result;
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
            return result;
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
            return result;
        }

        // 2. 加载本项目全部模块，构建 模块ID -> 模块 映射，用于沿父链追溯顶层模块
        Map<Long, SysModule> moduleMap =
                sysModuleMapper
                        .selectList(
                                Wrappers.<SysModule>lambdaQuery()
                                        .eq(SysModule::getProjectNo, AppContext.getProjectNo()))
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        SysModule::getId,
                                        module -> module,
                                        (first, second) -> first));

        // 3. 加载本项目全局表关联关系，用于业务数据逐层下钻
        List<SysTableRelation> relations =
                sysTableRelationMapper.selectList(
                        Wrappers.<SysTableRelation>lambdaQuery()
                                .eq(SysTableRelation::getProjectNo, AppContext.getProjectNo()));

        // 4. 逐编码解析顶层模块、主表、下钻路径与关联主体
        for (Map.Entry<String, String[]> entry : codePartsMap.entrySet()) {
            String code = entry.getKey();
            String[] parts = entry.getValue();
            Set<Long> moduleIds = codeModuleIdsMap.getOrDefault(code, Set.of());
            CodeMeta meta = null;
            for (Long moduleId : moduleIds) {
                SysModule module = moduleMap.get(moduleId);
                if (module == null) {
                    continue;
                }
                SysModule root = findTopmostAncestor(moduleId, moduleMap);
                if (root == null || !StringUtils.hasText(root.getPrimaryTable())) {
                    continue;
                }
                meta = new CodeMeta();
                meta.setCode(code);
                meta.setTableName(parts[0]);
                meta.setColumn(parts[1]);
                meta.setModuleId(moduleId);
                meta.setModuleName(module.getModuleName());
                meta.setRootModuleId(root.getId());
                meta.setRootPrimaryTable(root.getPrimaryTable());
                meta.setRootEnableActive(root.getEnableActive());
                meta.setRelationPath(
                        buildRelationPath(root.getPrimaryTable(), parts[0], relations));
                SysField fieldConfig = codeFieldMap.get(code);
                meta.setCloud(
                        fieldConfig != null
                                        && StringUtils.hasText(fieldConfig.getRelationBusinessNo())
                                ? CLOUD_YES
                                : CLOUD_NO);
                meta.setSubjectIds(resolveSubjectIds(module, projectSubjects));
                break;
            }
            if (meta != null) {
                result.put(code, meta);
            }
        }
        return result;
    }

    /**
     * 解析权限节点关联的主体ID列表。
     *
     * <p>取模块 {@code source_subjects}（为空时回退模块 {@code subject_id}），与当前项目主体取交集后返回。
     */
    private List<Long> resolveSubjectIds(
            SysModule module, List<SysProjectSubject> projectSubjects) {
        Set<Long> subjectIds = new LinkedHashSet<>();
        if (CollUtil.isNotEmpty(module.getSourceSubjects())) {
            module.getSourceSubjects().stream().filter(Objects::nonNull).forEach(subjectIds::add);
        } else if (module.getSubjectId() != null) {
            subjectIds.add(module.getSubjectId());
        }
        return projectSubjects.stream()
                .map(SysProjectSubject::getSubjectId)
                .filter(id -> id != null && subjectIds.contains(id))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 沿 {@code parent_id} 父链找到最顶层模块（{@code parent_id} 为 0/null 的模块）。
     *
     * @param moduleId 起始模块ID（来自 {@code sys_module_field}）
     * @param moduleMap 本项目全部模块映射
     * @return 顶层模块；父链存在环或起始模块不存在时返回起始模块本身
     */
    private SysModule findTopmostAncestor(Long moduleId, Map<Long, SysModule> moduleMap) {
        Long cur = moduleId;
        Set<Long> visited = new HashSet<>();
        while (cur != null && cur > 0L && visited.add(cur)) {
            SysModule module = moduleMap.get(cur);
            if (module == null) {
                return moduleMap.get(moduleId);
            }
            Long parentId = module.getParentId();
            if (parentId == null || parentId == 0L) {
                return module;
            }
            cur = parentId;
        }
        return moduleMap.get(moduleId);
    }

    /**
     * 在全局表关联关系中，用 BFS 计算从 {@code fromTable} 到 {@code toTable} 的逐层下钻路径。
     *
     * <p>关系图以 {@code mainTable -> joinTable} 为有向边，沿边从主表一路下钻到权限节点所在表。 返回有序的 {@link SysTableRelation}
     * 列表（每一项表示从某主表到其子表的关联）；若 {@code fromTable} 与 {@code toTable} 相同返回空列表；若不可达返回 {@code
     * null}（调用方据此回退到直接查询）。
     */
    private List<SysTableRelation> buildRelationPath(
            String fromTable, String toTable, List<SysTableRelation> relations) {
        if (!StringUtils.hasText(fromTable) || !StringUtils.hasText(toTable)) {
            return null;
        }
        if (fromTable.equalsIgnoreCase(toTable)) {
            return new ArrayList<>();
        }
        Map<String, List<SysTableRelation>> adjacency = new HashMap<>();
        if (CollUtil.isNotEmpty(relations)) {
            for (SysTableRelation rel : relations) {
                if (!StringUtils.hasText(rel.getMainTable())
                        || !StringUtils.hasText(rel.getJoinTable())) {
                    continue;
                }
                adjacency
                        .computeIfAbsent(rel.getMainTable().toLowerCase(), k -> new ArrayList<>())
                        .add(rel);
            }
        }
        String fromKey = fromTable.toLowerCase();
        String toKey = toTable.toLowerCase();
        Map<String, SysTableRelation> prevEdge = new HashMap<>();
        Map<String, String> prevTable = new HashMap<>();
        Set<String> visited = new HashSet<>();
        LinkedList<String> queue = new LinkedList<>();
        queue.add(fromKey);
        visited.add(fromKey);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(toKey)) {
                break;
            }
            for (SysTableRelation rel : adjacency.getOrDefault(current, Collections.emptyList())) {
                String joinKey = rel.getJoinTable().toLowerCase();
                if (visited.add(joinKey)) {
                    prevEdge.put(joinKey, rel);
                    prevTable.put(joinKey, current);
                    queue.add(joinKey);
                }
            }
        }
        if (!fromKey.equals(toKey) && !prevTable.containsKey(toKey)) {
            return null;
        }
        List<SysTableRelation> path = new ArrayList<>();
        String cur = toKey;
        while (!cur.equals(fromKey)) {
            SysTableRelation rel = prevEdge.get(cur);
            if (rel == null) {
                return null;
            }
            path.add(0, rel);
            cur = prevTable.get(cur);
            if (cur == null) {
                return null;
            }
        }
        return path;
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
     * <p>业务数据遵循「从顶层模块主表逐层下钻」规则：先以权限节点所属模块的<b>顶层模块主表</b>为起点，取 {@code active=1} （仅当顶层模块开启启停 {@code
     * enable_active=1}）且归属于当前主体的记录主键/关联字段，再结合 {@code sys_table_relation}
     * 计算出的逐层下钻路径，一层一层把可追溯的记录主键收窄，最终在权限节点所在表上按末级关联关系过滤出业务字段值。
     *
     * <p>若权限节点无可达顶层主表或未配置表关联路径，则<b>回退</b>为直接查询权限节点表（与原实现一致）。 结果按 主体ID -> 权限编码
     * 归集；字段值保持原始类型、数组按元素打散、同主体去重（见 {@link #dedupKey(Object)}）； 单个查询项失败仅记日志，不影响整体列表查询。
     */
    private Map<Long, Map<String, List<Object>>> queryBizData(
            String projectNo, List<CodeMeta> metas) {
        Map<Long, Map<String, List<Object>>> subjectBizDataMap = new HashMap<>();
        if (CollUtil.isEmpty(metas)) {
            return subjectBizDataMap;
        }

        DSLContext dsl;
        try {
            dsl = dataSourceResolver.getDSLContextByProjectNo(projectNo);
        } catch (Exception e) {
            log.error("项目 {} 数据源解析失败，无法查询业务数据", projectNo, e);
            return subjectBizDataMap;
        }

        // 去重键集合：主体ID#权限编码 -> 已收集业务数据的去重键
        Map<String, Set<Object>> seenBizNameKeys = new HashMap<>();
        for (CodeMeta meta : metas) {
            if (CollUtil.isEmpty(meta.getSubjectIds())
                    || !StringUtils.hasText(meta.getTableName())
                    || !StringUtils.hasText(meta.getColumn())) {
                continue;
            }
            String dataPermissionCode = meta.getTableName() + "*" + meta.getColumn();

            // 能够追溯到顶层主表并存在下钻路径时，走逐层下钻；否则回退直接查询
            boolean drillDown =
                    meta.getRelationPath() != null
                            && StringUtils.hasText(meta.getRootPrimaryTable());
            if (!drillDown) {
                queryBizDataDirect(
                        dsl, meta, dataPermissionCode, subjectBizDataMap, seenBizNameKeys);
                continue;
            }

            for (Long subjectId : meta.getSubjectIds()) {
                try {
                    // 起点：顶层模块主表，取 active=1 且归属于当前主体的关联字段值（作为下钻初始键集合）
                    String rootKeyField =
                            CollUtil.isEmpty(meta.getRelationPath())
                                    ? "id"
                                    : meta.getRelationPath().get(0).getMainField();
                    List<Object> currentKeys =
                            queryValues(
                                    dsl,
                                    meta.getRootPrimaryTable(),
                                    rootKeyField,
                                    null,
                                    null,
                                    Integer.valueOf(1).equals(meta.getRootEnableActive()),
                                    subjectId);
                    if (CollUtil.isEmpty(currentKeys)) {
                        continue;
                    }

                    List<SysTableRelation> path = meta.getRelationPath();
                    if (CollUtil.isEmpty(path)) {
                        // 权限节点表即顶层主表：直接按主键过滤取业务字段值
                        List<Object> biz =
                                queryValues(
                                        dsl,
                                        meta.getRootPrimaryTable(),
                                        meta.getColumn(),
                                        "id",
                                        currentKeys,
                                        false,
                                        null);
                        collectBiz(
                                biz,
                                meta.getCloud(),
                                subjectId,
                                dataPermissionCode,
                                subjectBizDataMap,
                                seenBizNameKeys);
                        continue;
                    }

                    // 沿路径逐层下钻：中间层取「下一层关联的主表字段」，末层取业务字段值
                    for (int i = 0; i < path.size(); i++) {
                        SysTableRelation rel = path.get(i);
                        if (i < path.size() - 1) {
                            String nextKeyField = path.get(i + 1).getMainField();
                            currentKeys =
                                    queryValues(
                                            dsl,
                                            rel.getJoinTable(),
                                            nextKeyField,
                                            rel.getJoinField(),
                                            currentKeys,
                                            false,
                                            null);
                            if (CollUtil.isEmpty(currentKeys)) {
                                break;
                            }
                        } else {
                            List<Object> biz =
                                    queryValues(
                                            dsl,
                                            rel.getJoinTable(),
                                            meta.getColumn(),
                                            rel.getJoinField(),
                                            currentKeys,
                                            false,
                                            null);
                            collectBiz(
                                    biz,
                                    meta.getCloud(),
                                    subjectId,
                                    dataPermissionCode,
                                    subjectBizDataMap,
                                    seenBizNameKeys);
                        }
                    }
                } catch (Exception e) {
                    log.error(
                            "下钻查询业务数据失败: projectNo={}, rootTable={}, table={}, column={}, subjectId={}",
                            projectNo,
                            meta.getRootPrimaryTable(),
                            meta.getTableName(),
                            meta.getColumn(),
                            subjectId,
                            e);
                }
            }
        }
        return subjectBizDataMap;
    }

    /**
     * 直接查询权限节点表（下钻不可达时的回退实现）。
     *
     * <p>{@code SELECT DISTINCT subject_id, 字段名 FROM 表名 WHERE subject_id IN (主体ID列表)}，结果按 主体ID ->
     * 权限编码归集。
     */
    private void queryBizDataDirect(
            DSLContext dsl,
            CodeMeta meta,
            String dataPermissionCode,
            Map<Long, Map<String, List<Object>>> subjectBizDataMap,
            Map<String, Set<Object>> seenBizNameKeys) {
        Field<String> subjectField = field(name("subject_id"), String.class);
        List<String> subjectIds =
                CollUtil.emptyIfNull(meta.getSubjectIds()).stream()
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .collect(Collectors.toList());
        if (subjectIds.isEmpty()
                || !StringUtils.hasText(meta.getTableName())
                || !StringUtils.hasText(meta.getColumn())) {
            return;
        }
        try {
            Field<Object> valueField = field(name(meta.getColumn()), Object.class);
            var records =
                    dsl.selectDistinct(subjectField, valueField)
                            .from(table(name(meta.getTableName())))
                            .where(subjectField.in(subjectIds))
                            .fetch();
            boolean cloud = CLOUD_YES.equals(meta.getCloud());
            for (Record record : records) {
                Long subjectId = parseSubjectId(record.get(subjectField));
                if (subjectId == null) {
                    continue;
                }
                List<Object> bizNames =
                        subjectBizDataMap
                                .computeIfAbsent(subjectId, key -> new HashMap<>())
                                .computeIfAbsent(dataPermissionCode, key -> new ArrayList<>());
                Set<Object> seenKeys =
                        seenBizNameKeys.computeIfAbsent(
                                subjectId + "#" + dataPermissionCode, key -> new HashSet<>());
                for (Map.Entry<Object, Object> entry :
                        flattenBizNames(record.get(valueField), cloud).entrySet()) {
                    if (seenKeys.add(entry.getKey())) {
                        bizNames.add(entry.getValue());
                    }
                }
            }
        } catch (Exception e) {
            log.error(
                    "回退直查业务数据失败: projectNo={}, table={}, column={}",
                    AppContext.getProjectNo(),
                    meta.getTableName(),
                    meta.getColumn(),
                    e);
        }
    }

    /**
     * 通用取值查询：从指定表读取 {@code selectField}，支持两种过滤方式。
     *
     * <ul>
     *   <li>下钻中间/末层：{@code inField IN (inKeys)}（{@code inField}/{@code inKeys} 非空时生效）；
     *   <li>顶层主表起点：{@code active=1}（{@code filterActive} 为真且模块开启启停时）+ {@code subject_id =
     *       subjectId} （{@code subjectId} 非空时生效）。
     * </ul>
     *
     * <p>返回原始 JDBC 类型的值列表（数字不转字符串），便于后续与业务字段类型对齐。
     */
    private List<Object> queryValues(
            DSLContext dsl,
            String table,
            String selectField,
            String inField,
            List<Object> inKeys,
            boolean filterActive,
            Long subjectId) {
        Field<Object> selectFieldRef = field(name(table, selectField), Object.class);
        List<Condition> conditions = new ArrayList<>();
        if (StringUtils.hasText(inField) && CollUtil.isNotEmpty(inKeys)) {
            conditions.add(field(name(table, inField)).in(toSqlValues(inKeys)));
        }
        if (filterActive) {
            conditions.add(field(name(table, "active")).eq(1));
        }
        if (subjectId != null) {
            conditions.add(field(name(table, "subject_id")).eq(subjectId));
        }
        var records =
                dsl.select(selectFieldRef)
                        .from(table(name(table)))
                        .where(conditions.isEmpty() ? noCondition() : and(conditions))
                        .fetch();
        List<Object> values = new ArrayList<>();
        for (Record record : records) {
            values.add(record.get(selectFieldRef));
        }
        return values;
    }

    /** 将一批原始业务值打散、去重后追加到「主体ID -> 权限编码」对应的业务数据列表中 */
    private void collectBiz(
            List<Object> rawValues,
            Integer cloud,
            Long subjectId,
            String dataPermissionCode,
            Map<Long, Map<String, List<Object>>> subjectBizDataMap,
            Map<String, Set<Object>> seenBizNameKeys) {
        if (CollUtil.isEmpty(rawValues)) {
            return;
        }
        boolean isCloud = CLOUD_YES.equals(cloud);
        List<Object> bizNames =
                subjectBizDataMap
                        .computeIfAbsent(subjectId, key -> new HashMap<>())
                        .computeIfAbsent(dataPermissionCode, key -> new ArrayList<>());
        Set<Object> seenKeys =
                seenBizNameKeys.computeIfAbsent(
                        subjectId + "#" + dataPermissionCode, key -> new HashSet<>());
        for (Object value : rawValues) {
            for (Map.Entry<Object, Object> entry : flattenBizNames(value, isCloud).entrySet()) {
                if (seenKeys.add(entry.getKey())) {
                    bizNames.add(entry.getValue());
                }
            }
        }
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

    /** 将业务值列表转换为可安全绑定到 SQL 的值（JSON/集合转文本，标量原样），避免把复合类型直接绑定给 JDBC */
    private List<Object> toSqlValues(List<Object> values) {
        return values.stream().map(JsonValueUtils::toSqlValue).collect(Collectors.toList());
    }

    /** 单个数据权限节点的解析元数据，贯穿「模块名称 / 顶层主表 / 下钻路径 / 关联主体」 */
    @lombok.Data
    private static class CodeMeta {
        /** 权限编码（表名*字段名） */
        private String code;

        /** 权限节点所在表名 */
        private String tableName;

        /** 权限节点字段名（业务数据取值列） */
        private String column;

        /** sys_module_field 命中的模块ID */
        private Long moduleId;

        /** 命中模块的模块名称 */
        private String moduleName;

        /** 命中模块沿父链追溯得到的顶层模块ID */
        private Long rootModuleId;

        /** 顶层模块主表名（业务数据下钻起点） */
        private String rootPrimaryTable;

        /** 顶层模块是否开启启停（决定起点是否过滤 active=1） */
        private Integer rootEnableActive;

        /** 从顶层主表到权限节点所在表的逐层下钻路径；空列表表示两者为同一表；null 表示不可达 */
        private List<SysTableRelation> relationPath;

        /** 权限节点关联的主体ID列表（模块数据来源主体 ∩ 项目主体） */
        private List<Long> subjectIds;

        /** 是否来自词云：0-否，1-是 */
        private Integer cloud;
    }
}
