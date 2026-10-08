package com.jdec.platform.config.biz.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.dto.request.SaveStatusReq;
import com.jdec.platform.config.api.dto.request.SysStatusDragReq;
import com.jdec.platform.config.api.dto.request.SysStatusSaveReq;
import com.jdec.platform.config.api.dto.request.SysStatusToggleReq;
import com.jdec.platform.config.api.dto.response.ModuleApprovalChainConfigResp;
import com.jdec.platform.config.api.dto.response.SysStatusTreeResp;
import com.jdec.platform.config.biz.entity.SysDataSnapshot;
import com.jdec.platform.config.biz.entity.SysModuleStatus;
import com.jdec.platform.config.biz.entity.SysStatus;
import com.jdec.platform.config.biz.mapper.SysDataSnapshotMapper;
import com.jdec.platform.config.biz.mapper.SysModuleStatusMapper;
import com.jdec.platform.config.biz.mapper.SysStatusMapper;
import com.jdec.platform.config.biz.reference.ReferenceCheckService;
import com.jdec.platform.config.biz.reference.ReferenceViolation;
import com.jdec.platform.shared.audit.annotation.DataAudit;
import com.jdec.platform.shared.audit.enums.OperationType;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.exception.PopException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/** 状态 Service 实现 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysStatusService implements SysStatusApi {

    private final SysStatusMapper sysStatusMapper;
    private final ReferenceCheckService referenceCheckService;
    private final SysDataSnapshotMapper sysDataSnapshotMapper;
    private final SysModuleStatusMapper sysModuleStatusMapper;

    /**
     * 延迟获取自身代理，避免构造循环依赖。
     *
     * <p>整棵树保存时经代理调用 {@link #saveStatusNode(SysStatusSaveReq)}，确保 {@code @DataAudit} 等切面在节点级保存上生效。
     */
    private final ObjectProvider<SysStatusService> selfProvider;

    @Override
    public List<SysStatusTreeResp> tree() {
        List<SysStatus> list =
                sysStatusMapper.selectList(
                        new LambdaQueryWrapper<SysStatus>()
                                .eq(SysStatus::getProjectNo, AppContext.getProjectNo())
                                .eq(SysStatus::getSubjectId, AppContext.getSubjectId())
                                .orderByAsc(SysStatus::getSortOrder)
                                .orderByAsc(SysStatus::getStatusValue)
                                .orderByAsc(SysStatus::getId));
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        // 构建映射
        Map<Long, SysStatusTreeResp> nodeMap = new LinkedHashMap<>();
        for (SysStatus status : list) {
            SysStatusTreeResp resp = new SysStatusTreeResp();
            BeanUtils.copyProperties(status, resp);
            resp.setChildren(new ArrayList<>());
            nodeMap.put(status.getId(), resp);
        }
        // 组装树
        List<SysStatusTreeResp> tree = new ArrayList<>();
        for (SysStatus status : list) {
            SysStatusTreeResp node = nodeMap.get(status.getId());
            Long pid = status.getPid();
            if (pid == null || pid == 0L) {
                tree.add(node);
            } else {
                SysStatusTreeResp parent = nodeMap.get(pid);
                if (parent != null && parent.getChildren() != null) {
                    parent.getChildren().add(node);
                } else {
                    // 父节点不存在或已删除，挂到顶层
                    tree.add(node);
                }
            }
        }
        return tree;
    }

    @Transactional
    @Override
    public Long saveStatus(SaveStatusReq request) {
        if (request == null || request.getStatus() == null) {
            throw new BusinessException("状态信息不能为空");
        }
        SysStatusSaveReq node = request.getStatus();
        boolean hasTree = !CollectionUtils.isEmpty(request.getTreeNodes());
        if (hasTree) {
            // 携带整棵树时，当前节点位置以 treeNodes 为准，保证同级重名/父级等单节点校验分组正确
            fillOwnPositionFromTree(node, request.getTreeNodes());
        }
        // 经自身代理调用单节点保存，确保 @DataAudit 审计生效
        Long statusId = selfProvider.getObject().saveStatusNode(node);
        if (hasTree) {
            // 整棵树父子关系与同级排序同步（参考模块 saveModule 的树节点拓扑同步逻辑）
            updateStatusTreeNodes(statusId, request.getTreeNodes());
        }
        return statusId;
    }

    /**
     * 单节点状态新增/编辑：id 为空则新增，否则编辑。
     *
     * <p>供整棵树保存接口经自身代理调用，以保留 {@code @DataAudit} 审计。
     */
    @DataAudit(
            module = "系统设置",
            subModule = "状态配置",
            operation = OperationType.UPDATE,
            tableName = "sys_status",
            dataIdField = "#req.id")
    @Transactional
    public Long saveStatusNode(SysStatusSaveReq req) {
        if (req.getId() == null) {
            // 新增
            SysStatus entity = new SysStatus();
            BeanUtils.copyProperties(req, entity);
            if (entity.getPid() == null) {
                entity.setPid(0L);
            }
            entity.setSubjectId(AppContext.getSubjectId());
            entity.setProjectNo(AppContext.getProjectNo());
            // 校验同级名称是否重复（按主体、应用隔离）
            long count =
                    sysStatusMapper.selectCount(
                            new LambdaQueryWrapper<SysStatus>()
                                    .eq(SysStatus::getPid, entity.getPid())
                                    .eq(SysStatus::getSubjectId, AppContext.getSubjectId())
                                    .eq(SysStatus::getProjectNo, AppContext.getProjectNo())
                                    .eq(SysStatus::getTitle, entity.getTitle()));
            if (count > 0) {
                throw new BusinessException("同级状态下名称已存在");
            }
            // 处理排序值
            handleSortOrderForInsert(entity);
            sysStatusMapper.insert(entity);
            // 手动创建快照（此时已拿到MyBatis-Plus回填的主键ID）
            SysDataSnapshot snapshot = new SysDataSnapshot();
            snapshot.setTableName("sys_status");
            snapshot.setDataId(entity.getId());
            snapshot.setJsonData(JSONUtil.toJsonStr(entity));
            sysDataSnapshotMapper.insert(snapshot);
            return entity.getId();
        } else {
            // 编辑
            boolean exists =
                    sysStatusMapper.exists(
                            Wrappers.<SysStatus>lambdaQuery()
                                    .eq(SysStatus::getId, req.getId())
                                    .eq(SysStatus::getSubjectId, AppContext.getSubjectId())
                                    .eq(SysStatus::getProjectNo, AppContext.getProjectNo()));
            if (!exists) {
                throw new BusinessException("状态不存在");
            }
            SysStatus oldEntity = sysStatusMapper.selectById(req.getId());
            SysStatus entity = new SysStatus();
            BeanUtils.copyProperties(req, entity);
            entity.setId(req.getId());
            entity.setSubjectId(AppContext.getSubjectId());
            // 不能把自己设为自己的父节点
            if (req.getId().equals(req.getPid())) {
                throw new BusinessException("不能将自己设为父状态");
            }
            // 校验是否形成了环（不能把自己子节点设为父节点）
            if (req.getPid() != null && req.getPid() != 0L) {
                checkCycle(req.getId(), req.getPid(), AppContext.getSubjectId());
                // 校验同级名称是否重复（按主体、应用隔离）
                Long count =
                        sysStatusMapper.selectCount(
                                Wrappers.<SysStatus>lambdaQuery()
                                        .eq(SysStatus::getPid, req.getPid())
                                        .eq(SysStatus::getSubjectId, AppContext.getSubjectId())
                                        .eq(SysStatus::getProjectNo, AppContext.getProjectNo())
                                        .eq(SysStatus::getTitle, req.getTitle())
                                        .ne(SysStatus::getId, req.getId()));
                if (count > 0) {
                    throw new BusinessException("同级状态下名称已存在");
                }
            }
            // 排序由前端整棵树决定，sortOrder 非空则按原值直接更新，为空则 updateById 不覆盖该列
            sysStatusMapper.updateById(entity);

            // 停用时级联停用所有子节点（enabled / approvalShowFlag）
            Long subjectId = AppContext.getSubjectId();
            Set<Long> childIds = collectChildrenIds(req.getId(), subjectId);
            if (!childIds.isEmpty()) {
                boolean disableEnabled =
                        Integer.valueOf(0).equals(req.getEnabled())
                                && !Integer.valueOf(0).equals(oldEntity.getEnabled());
                boolean disableApproval =
                        Integer.valueOf(0).equals(req.getApprovalShowFlag())
                                && !Integer.valueOf(0).equals(oldEntity.getApprovalShowFlag());
                if (disableEnabled || disableApproval) {
                    var updateWrapper =
                            Wrappers.<SysStatus>lambdaUpdate().in(SysStatus::getId, childIds);
                    if (disableEnabled) {
                        updateWrapper.set(SysStatus::getEnabled, 0);
                    }
                    if (disableApproval) {
                        updateWrapper.set(SysStatus::getApprovalShowFlag, 0);
                    }
                    sysStatusMapper.update(null, updateWrapper);
                }
            }
            return req.getId();
        }
    }

    @DataAudit(
            module = "系统设置",
            subModule = "状态配置",
            operation = OperationType.DELETE,
            tableName = "sys_status",
            deleteDisplayField = "title",
            entityClass = SysStatus.class,
            dataIdField = "#id")
    @Transactional
    @Override
    public void deleteStatus(Long id, boolean force) {
        SysStatus status =
                sysStatusMapper.selectOne(
                        Wrappers.<SysStatus>lambdaQuery()
                                .eq(SysStatus::getId, id)
                                .eq(SysStatus::getSubjectId, AppContext.getSubjectId())
                                .eq(SysStatus::getProjectNo, AppContext.getProjectNo()));
        if (status == null) {
            throw new BusinessException("状态不存在");
        }
        // 引用检查
        List<ReferenceViolation> violations =
                referenceCheckService.check(SysStatus.class, List.of(status.getId()));
        if (!violations.isEmpty()) {
            throw new PopException(
                    referenceCheckService.buildMessage(status.getTitle(), violations));
        }
        sysStatusMapper.deleteById(id);
        // 删除后重排同级节点的排序值
        reorderSiblingsAfterDelete(status.getPid(), AppContext.getSubjectId());
    }

    @DataAudit(
            module = "系统设置",
            subModule = "状态配置",
            operation = OperationType.UPDATE,
            tableName = "sys_status",
            dataIdField = "#req.id")
    @Transactional
    @Override
    public void dragStatus(SysStatusDragReq req) {
        Long id = req.getId();
        Long targetPid = req.getTargetPid();
        if (id == null) {
            throw new BusinessException("节点ID不能为空");
        }
        SysStatus node =
                sysStatusMapper.selectOne(
                        Wrappers.<SysStatus>lambdaQuery()
                                .eq(SysStatus::getId, id)
                                .eq(SysStatus::getSubjectId, AppContext.getSubjectId())
                                .eq(SysStatus::getProjectNo, AppContext.getProjectNo()));
        if (node == null) {
            throw new BusinessException("状态不存在");
        }
        if (targetPid == null) {
            targetPid = 0L;
        }
        // 不能拖到自己下面
        if (id.equals(targetPid)) {
            throw new BusinessException("不能将自己设为自己的父节点");
        }
        // 不能拖到自己的子节点下面（形成环）
        if (targetPid != 0L) {
            checkCycle(id, targetPid, AppContext.getSubjectId());
            boolean exists =
                    sysStatusMapper.exists(
                            Wrappers.<SysStatus>lambdaQuery()
                                    .eq(SysStatus::getId, targetPid)
                                    .eq(SysStatus::getSubjectId, AppContext.getSubjectId())
                                    .eq(SysStatus::getProjectNo, AppContext.getProjectNo()));
            if (!exists) {
                throw new BusinessException("目标父状态不存在");
            }
        }
        // 校验同级名称是否重复
        Long count =
                sysStatusMapper.selectCount(
                        Wrappers.<SysStatus>lambdaQuery()
                                .eq(SysStatus::getPid, targetPid)
                                .eq(SysStatus::getSubjectId, AppContext.getSubjectId())
                                .eq(SysStatus::getProjectNo, AppContext.getProjectNo())
                                .eq(SysStatus::getTitle, node.getTitle())
                                .ne(SysStatus::getId, id));
        if (count > 0) {
            throw new BusinessException("同级状态下名称已存在");
        }
        node.setPid(targetPid);
        // 处理排序值
        handleSortOrderForDrag(node, req.getSortOrder());
        sysStatusMapper.updateById(node);
    }

    /** 收集所有子节点ID */
    private Set<Long> collectChildrenIds(Long parentId, Long subjectId) {
        Set<Long> result = new HashSet<>();
        List<SysStatus> children =
                sysStatusMapper.selectList(
                        Wrappers.<SysStatus>lambdaQuery()
                                .eq(SysStatus::getPid, parentId)
                                .eq(SysStatus::getSubjectId, subjectId)
                                .eq(SysStatus::getProjectNo, AppContext.getProjectNo()));
        for (SysStatus child : children) {
            result.add(child.getId());
            result.addAll(collectChildrenIds(child.getId(), subjectId));
        }
        return result;
    }

    @DataAudit(
            module = "系统设置",
            subModule = "状态配置",
            operation = OperationType.UPDATE,
            tableName = "sys_status",
            dataIdField = "#req.id")
    @Transactional
    @Override
    public void toggleStatus(SysStatusToggleReq req) {
        if (req.getId() == null) {
            throw new BusinessException("状态ID不能为空");
        }
        if (req.getEnabled() == null || (req.getEnabled() != 0 && req.getEnabled() != 1)) {
            throw new BusinessException("enabled 参数必须为 0 或 1");
        }

        SysStatus status =
                sysStatusMapper.selectOne(
                        Wrappers.<SysStatus>lambdaQuery()
                                .eq(SysStatus::getId, req.getId())
                                .eq(SysStatus::getSubjectId, AppContext.getSubjectId())
                                .eq(SysStatus::getProjectNo, AppContext.getProjectNo()));
        if (status == null) {
            throw new BusinessException("状态不存在");
        }

        status.setEnabled(req.getEnabled());
        sysStatusMapper.updateById(status);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ModuleApprovalChainConfigResp.StatusNode getStatusByValue(
            Integer statusValue, Long moduleId) {
        LambdaQueryWrapper<SysModuleStatus> moduleWrapper = new LambdaQueryWrapper<>();
        moduleWrapper.eq(SysModuleStatus::getModuleId, moduleId);
        List<SysModuleStatus> IdList = sysModuleStatusMapper.selectList(moduleWrapper);
        Set<Long> Ids =
                IdList.stream().map(SysModuleStatus::getStatusId).collect(Collectors.toSet());
        LambdaQueryWrapper<SysStatus> statusWrapper = new LambdaQueryWrapper<>();
        statusWrapper
                .eq(SysStatus::getStatusValue, statusValue)
                .in(SysStatus::getId, Ids)
                .orderByDesc(SysStatus::getId)
                .last("limit 1");
        SysStatus statusConfig = sysStatusMapper.selectOne(statusWrapper);
        if (Objects.isNull(statusConfig)) {
            throw new BusinessException("状态值 " + statusValue + " 不存在");
        }
        ModuleApprovalChainConfigResp.StatusNode node =
                new ModuleApprovalChainConfigResp.StatusNode();
        node.setId(statusConfig.getId());
        node.setPid(statusConfig.getPid());
        node.setTitle(statusConfig.getTitle());
        node.setStatusValue(statusConfig.getStatusValue());
        node.setStatusBackground(statusConfig.getStatusBackground());
        node.setStatusFontColor(statusConfig.getStatusFontColor());
        return node;
    }

    /** 整棵树保存时，将当前节点在 treeNodes 中的位置回填到节点请求，保证单节点校验分组正确 */
    private void fillOwnPositionFromTree(
            SysStatusSaveReq node, List<SaveStatusReq.StatusTreeNode> treeNodes) {
        SaveStatusReq.StatusTreeNode own = findStatusTreeNode(treeNodes, node.getId());
        if (own == null) {
            return;
        }
        if (own.getParentId() != null) {
            node.setPid(own.getParentId());
        }
        if (node.getId() == null && own.getSortOrder() != null) {
            node.setSortOrder(own.getSortOrder());
        }
    }

    /** 在整棵树的扁平节点中查找与指定状态ID匹配的节点（新增状态按空或0匹配） */
    private SaveStatusReq.StatusTreeNode findStatusTreeNode(
            List<SaveStatusReq.StatusTreeNode> treeNodes, Long statusId) {
        for (SaveStatusReq.StatusTreeNode node : treeNodes) {
            boolean matched;
            if (statusId == null) {
                matched = node.getId() == null || node.getId() == 0L;
            } else {
                matched = statusId.equals(node.getId());
            }
            if (matched) {
                return node;
            }
        }
        return null;
    }

    /**
     * 同步整棵状态树的父子关系与同级排序（参考模块 saveModule 的树节点拓扑同步）。
     *
     * <p>节点 id 为空或 0 视为本次新增的当前状态，替换为保存后生成的真实ID；仅对实际发生变化的节点执行更新， 请求树中缺失的节点保持原样不做删除处理。
     */
    private void updateStatusTreeNodes(
            Long currentSavedStatusId, List<SaveStatusReq.StatusTreeNode> treeNodes) {
        if (CollectionUtils.isEmpty(treeNodes)) {
            return;
        }
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();

        // 1. 查询当前主体与应用下全部状态
        List<SysStatus> allStatuses =
                sysStatusMapper.selectList(
                        Wrappers.<SysStatus>lambdaQuery()
                                .eq(SysStatus::getProjectNo, projectNo)
                                .eq(SysStatus::getSubjectId, subjectId));
        Map<Long, SysStatus> currentMap =
                allStatuses.stream().collect(Collectors.toMap(SysStatus::getId, s -> s));

        // 2. 规整节点：id 为空或 0 表示本次新增的当前状态，替换为真实生成的 ID
        List<SaveStatusReq.StatusTreeNode> normalizedNodes = new ArrayList<>();
        for (SaveStatusReq.StatusTreeNode node : treeNodes) {
            SaveStatusReq.StatusTreeNode normalized = new SaveStatusReq.StatusTreeNode();
            Long nid =
                    (node.getId() == null || node.getId() == 0L)
                            ? currentSavedStatusId
                            : node.getId();
            Long npid =
                    (node.getParentId() == null || node.getParentId() == 0L)
                            ? 0L
                            : node.getParentId();
            normalized.setId(nid);
            normalized.setParentId(npid);
            normalized.setSortOrder(node.getSortOrder() != null ? node.getSortOrder() : 0);
            normalizedNodes.add(normalized);
        }

        // 3. 校验节点均属于当前主体与应用，并组装最终的父级映射
        Map<Long, Long> nextParentMap = new HashMap<>();
        for (SysStatus status : allStatuses) {
            nextParentMap.put(status.getId(), status.getPid() != null ? status.getPid() : 0L);
        }
        for (SaveStatusReq.StatusTreeNode node : normalizedNodes) {
            if (!currentMap.containsKey(node.getId())) {
                throw new BusinessException(400, "待调整树结构的状态不存在或无权操作: " + node.getId());
            }
            if (node.getParentId() != 0L && !currentMap.containsKey(node.getParentId())) {
                throw new BusinessException(400, "目标父状态不存在或无权操作: " + node.getParentId());
            }
            nextParentMap.put(node.getId(), node.getParentId());
        }

        // 4. 防环校验：沿父级链路向上遍历，防止出现环状依赖
        for (Long startId : nextParentMap.keySet()) {
            Set<Long> visited = new HashSet<>();
            Long cur = startId;
            while (cur != null && cur != 0L) {
                if (!visited.add(cur)) {
                    throw new BusinessException(400, "状态树结构存在循环引用，调整失败");
                }
                cur = nextParentMap.get(cur);
            }
        }

        // 5. 差异比对：仅对实际发生父级或同级排序变化的节点执行更新
        List<SysStatus> updateList = new ArrayList<>();
        for (SaveStatusReq.StatusTreeNode node : normalizedNodes) {
            SysStatus current = currentMap.get(node.getId());
            if (current == null) {
                continue;
            }
            Long currentPid = current.getPid() != null ? current.getPid() : 0L;
            Integer currentSortOrder = current.getSortOrder() != null ? current.getSortOrder() : 0;
            if (!Objects.equals(currentPid, node.getParentId())
                    || !Objects.equals(currentSortOrder, node.getSortOrder())) {
                SysStatus toUpdate = new SysStatus();
                toUpdate.setId(current.getId());
                toUpdate.setPid(node.getParentId());
                toUpdate.setSortOrder(node.getSortOrder());
                updateList.add(toUpdate);
            }
        }

        if (!updateList.isEmpty()) {
            updateList.forEach(sysStatusMapper::updateById);
            log.info("同步更新了 {} 个状态树节点的位置与排序", updateList.size());
        }
    }

    /** 检查是否形成环：targetPid 不能是 id 的子节点（包括自身） */
    private void checkCycle(Long id, Long targetPid, Long subjectId) {
        if (id.equals(targetPid)) {
            throw new BusinessException("不能将节点移动到自身或其子节点下");
        }
        List<SysStatus> children =
                sysStatusMapper.selectList(
                        Wrappers.<SysStatus>lambdaQuery()
                                .eq(SysStatus::getPid, id)
                                .eq(SysStatus::getSubjectId, subjectId)
                                .eq(SysStatus::getProjectNo, AppContext.getProjectNo()));
        for (SysStatus child : children) {
            checkCycle(child.getId(), targetPid, subjectId);
        }
    }

    /** 处理新增时的排序值：排序由前端决定，已传则直接入库；未传时追加到同级末尾，后端不再搬移其它同级节点 */
    private void handleSortOrderForInsert(SysStatus entity) {
        if (entity.getSortOrder() == null) {
            // 前端未传排序值，追加到同级末尾
            Integer maxSort =
                    sysStatusMapper
                            .selectList(
                                    Wrappers.<SysStatus>lambdaQuery()
                                            .eq(SysStatus::getPid, entity.getPid())
                                            .eq(SysStatus::getSubjectId, AppContext.getSubjectId())
                                            .eq(SysStatus::getProjectNo, AppContext.getProjectNo())
                                            .orderByDesc(SysStatus::getSortOrder)
                                            .last("LIMIT 1"))
                            .stream()
                            .findFirst()
                            .map(SysStatus::getSortOrder)
                            .orElse(-1);
            entity.setSortOrder(maxSort + 1);
        }
    }

    /** 处理拖拽时的排序值 */
    private void handleSortOrderForDrag(SysStatus entity, Integer targetIndex) {
        if (targetIndex == null) {
            // 前端未传顺序值，自动分配为同级最大值+1
            Integer maxSort =
                    sysStatusMapper
                            .selectList(
                                    Wrappers.<SysStatus>lambdaQuery()
                                            .eq(SysStatus::getPid, entity.getPid())
                                            .eq(SysStatus::getSubjectId, AppContext.getSubjectId())
                                            .eq(SysStatus::getProjectNo, AppContext.getProjectNo())
                                            .ne(SysStatus::getId, entity.getId())
                                            .orderByDesc(SysStatus::getSortOrder)
                                            .last("LIMIT 1"))
                            .stream()
                            .findFirst()
                            .map(SysStatus::getSortOrder)
                            .orElse(-1);
            entity.setSortOrder(maxSort + 1);
        } else {
            // 前端传了顺序值（从0开始），需要调整同级节点的排序
            adjustSortOrderForDrag(entity, targetIndex);
        }
    }

    /**
     * 处理拖拽时的排序调整
     *
     * @param entity 被拖拽的节点
     * @param targetIndex 目标位置的顺序值（从0开始）
     */
    private void adjustSortOrderForDrag(SysStatus entity, Integer targetIndex) {
        // 查询同级所有节点（排除当前拖拽节点）
        List<SysStatus> siblings =
                sysStatusMapper.selectList(
                        Wrappers.<SysStatus>lambdaQuery()
                                .eq(SysStatus::getPid, entity.getPid())
                                .eq(SysStatus::getSubjectId, AppContext.getSubjectId())
                                .eq(SysStatus::getProjectNo, AppContext.getProjectNo())
                                .ne(SysStatus::getId, entity.getId())
                                .orderByAsc(SysStatus::getSortOrder));

        // 将拖拽节点插入到目标位置
        siblings.add(targetIndex, entity);

        // 批量更新所有节点的排序值（从0开始）
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).setSortOrder(i);
        }
        // 使用 MyBatis-Plus 批量更新
        if (!siblings.isEmpty()) {
            sysStatusMapper.updateById(siblings);
        }
    }

    /**
     * 删除节点后重排同级节点的排序值
     *
     * @param pid 父节点ID
     * @param subjectId 主体ID
     */
    private void reorderSiblingsAfterDelete(Long pid, Long subjectId) {
        // 查询同级所有节点
        List<SysStatus> siblings =
                sysStatusMapper.selectList(
                        Wrappers.<SysStatus>lambdaQuery()
                                .eq(SysStatus::getPid, pid)
                                .eq(SysStatus::getSubjectId, subjectId)
                                .eq(SysStatus::getProjectNo, AppContext.getProjectNo())
                                .orderByAsc(SysStatus::getSortOrder));

        if (siblings.isEmpty()) {
            return;
        }

        // 重新分配排序值（从0开始）
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).setSortOrder(i);
        }
        sysStatusMapper.updateById(siblings);
    }
}
