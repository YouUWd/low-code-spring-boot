package com.jdec.platform.config.biz.hr;

import com.jdec.platform.config.api.dto.response.UserSearchTreeNodeResp;
import com.jdec.platform.config.biz.entity.SysUser;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/**
 * 人力库（hr_manage）直查服务。
 *
 * <p>配置中心不再依赖 hr 模块，原先通过 {@code UserApi} / {@code DepartmentApi} / {@code SubjectApi} 获取的人力数据，改由本类用
 * jOOQ 直连 {@code hr_manage} 数据源查询。
 *
 * <p>本类不另外定义实体模型：用户行直接映射到 {@link SysUser}（人力字段与 sys_user 同名字段对齐）， 部门行映射到 {@link
 * UserSearchTreeNodeResp} 或 {@code Map<部门ID, 负责人姓名>}，主体行以 jOOQ {@link Record} 返回（调用方按 {@code id} /
 * {@code subject_name} / {@code company_name} 取值）。
 *
 * <p>数据源通过 {@link DataSourceResolver#getDSLContext(String)} 显式指定，不参与线程数据源路由，因此在
 * {@code @DataSource(CONFIG_CENTER)} 的 Service 内、以及 Spring 事务中调用均安全。
 *
 * <p>查询语义对齐原 hr 模块实现：不按 {@code subject_id} / {@code project_no} 隔离；{@code user} 表 {@code is_delete
 * = 1} 表示有效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrQueryService {

    private static final Table<?> HR_USER = DSL.table(DSL.name("user"));
    private static final Table<?> HR_DEPARTMENT = DSL.table(DSL.name("department"));
    private static final Table<?> HR_SUBJECT = DSL.table(DSL.name("subject"));

    /** 根节点的父ID */
    private static final String ROOT_PID = "D0";

    /** 部门ID前缀 */
    private static final String DEPART_ID_PREFIX = "D";

    /** 用户ID前缀 */
    private static final String USER_ID_PREFIX = "U";

    /** 节点类型：部门 */
    private static final Integer NODE_TYPE_DEPARTMENT = 1;

    /** 节点类型：用户 */
    private static final Integer NODE_TYPE_USER = 3;

    private final DataSourceResolver dataSourceResolver;

    // ==================== 用户 ====================

    /**
     * 按用户ID批量查询有效用户（{@code is_delete = 1}）。
     *
     * <p>返回的 {@link SysUser} 只承载人力字段：{@code userId} 为人力库用户ID，{@code departIds} / {@code departName}
     * / {@code positionName} 分别对应人力的部门ID串 / 部门名 / 岗位名。
     *
     * @param ids 用户ID集合，为空返回空列表
     * @return 用户列表
     */
    public List<SysUser> listUsersByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return selectUsers()
                .where(longField("id").in(ids))
                .and(intField("is_delete").eq(1))
                .fetchInto(SysUser.class);
    }

    /**
     * 按主体ID查询用户。
     *
     * <p>与原 hr 实现一致：不过滤 {@code is_delete}。
     *
     * @param subjectId 主体ID
     * @return 用户列表
     */
    public List<SysUser> listUsersBySubjectId(Long subjectId) {
        if (subjectId == null) {
            return Collections.emptyList();
        }
        return selectUsers().where(longField("subject_id").eq(subjectId)).fetchInto(SysUser.class);
    }

    /**
     * 按手机号查询企业微信用户ID（即 {@code user.short_name}）。
     *
     * @param phone 手机号
     * @return 企业微信用户ID，不存在返回空
     */
    public Optional<String> getWeChatUserIdByPhone(String phone) {
        String shortName =
                dsl().select(strField("short_name"))
                        .from(HR_USER)
                        .where(strField("phone").eq(phone))
                        .limit(1)
                        .fetchOne(0, String.class);
        return Optional.ofNullable(shortName);
    }

    // ==================== 部门 ====================

    /**
     * 全部部门（不过滤状态），与原 {@code DepartmentApi#getDepartmentList} 一致。
     *
     * @return 部门ID → 部门负责人姓名，负责人为空的部门不返回
     */
    public Map<Long, String> managerNameByDeptId() {
        Map<Long, String> managers = new LinkedHashMap<>();
        dsl().select(longField("id"), strField("manager_name"))
                .from(HR_DEPARTMENT)
                .fetch()
                .forEach(
                        record -> {
                            String managerName = record.get("manager_name", String.class);
                            if (managerName != null) {
                                managers.put(record.get("id", Long.class), managerName);
                            }
                        });
        return managers;
    }

    /**
     * 全部部门节点（不过滤状态），供调用方直接挂载用户后建树。
     *
     * <p>节点字段：{@code id} = 「D + 部门ID」、{@code label} = 部门名、{@code pid} = 「D + 父部门ID」， 顶层部门父节点为
     * {@code D0}；{@code children} 初始为空列表。
     *
     * @return 部门节点列表
     */
    public List<UserSearchTreeNodeResp> listDepartmentNodes() {
        return fetchDepartmentNodes(DSL.noCondition());
    }

    // ==================== 主体 ====================

    /**
     * 全部主体。
     *
     * <p>返回原始行，列名固定为 {@code id} / {@code subject_name} / {@code company_name}，调用方按需取值。
     *
     * @return 主体行列表
     */
    public List<Record> listSubjects() {
        return new ArrayList<>(
                dsl().select(longField("id"), strField("subject_name"), strField("company_name"))
                        .from(HR_SUBJECT)
                        .fetch());
    }

    // ==================== 人员架构树 ====================

    /**
     * 人员架构树（部门 + 用户）。
     *
     * <p>与原 {@code UserApi#getUserTreeList} 一致：只取 {@code department.status = 1} 与 {@code user.state
     * = 1 AND is_delete = 1}；每个用户按其 {@code department_id}（逗号分隔多值）逐个部门挂载。
     *
     * @param userType 用户类型，2=外部直接返回空列表（当前项目仅支持内部用户树）
     * @return 人员架构树
     */
    public List<UserSearchTreeNodeResp> getUserTreeList(Integer userType) {
        if (Integer.valueOf(2).equals(userType)) {
            return Collections.emptyList();
        }

        List<UserSearchTreeNodeResp> departments = fetchDepartmentNodes(intField("status").eq(1));
        if (departments.isEmpty()) {
            return Collections.emptyList();
        }

        List<SysUser> users =
                selectUsers()
                        .where(intField("state").eq(1))
                        .and(intField("is_delete").eq(1))
                        .fetchInto(SysUser.class);

        // 用户按所属部门分桶（同一用户可挂多个部门）
        Map<String, List<UserSearchTreeNodeResp>> deptUsers = new LinkedHashMap<>();
        for (SysUser user : users) {
            String deptIds = user.getDepartIds();
            if (deptIds == null || deptIds.isEmpty()) {
                continue;
            }
            for (String deptId : deptIds.split(",")) {
                deptId = deptId.trim();
                if (deptId.isEmpty()) {
                    continue;
                }
                UserSearchTreeNodeResp userNode =
                        UserSearchTreeNodeResp.builder()
                                .id(USER_ID_PREFIX + user.getUserId())
                                .sysId(user.getUserId())
                                .label(user.getUserName())
                                .departId(DEPART_ID_PREFIX + deptId)
                                .sex(user.getSex() != null ? user.getSex() : 1)
                                .avatar(user.getUserAvatar())
                                .workNumber(
                                        user.getWorkNumber() != null ? user.getWorkNumber() : "")
                                .type(NODE_TYPE_USER)
                                .departName(
                                        user.getDepartName() != null ? user.getDepartName() : "")
                                .build();
                deptUsers
                        .computeIfAbsent(DEPART_ID_PREFIX + deptId, key -> new ArrayList<>())
                        .add(userNode);
            }
        }

        for (UserSearchTreeNodeResp dept : departments) {
            dept.setChildren(
                    new ArrayList<>(deptUsers.getOrDefault(dept.getId(), new ArrayList<>())));
        }

        return buildNestedTree(departments, ROOT_PID);
    }

    /** 构建嵌套树结构，父节点缺失时按根节点处理，避免整棵子树被丢掉 */
    private List<UserSearchTreeNodeResp> buildNestedTree(
            List<UserSearchTreeNodeResp> items, String rootPid) {
        Map<String, UserSearchTreeNodeResp> nodeMap = new LinkedHashMap<>();
        for (UserSearchTreeNodeResp item : items) {
            nodeMap.put(item.getId(), item);
        }

        List<UserSearchTreeNodeResp> tree = new ArrayList<>();
        for (UserSearchTreeNodeResp item : items) {
            String pid = item.getPid();
            UserSearchTreeNodeResp parent = nodeMap.get(pid);
            boolean isRoot = (pid == null || pid.equals(rootPid) || parent == null);
            if (isRoot) {
                tree.add(item);
            } else {
                List<UserSearchTreeNodeResp> children = parent.getChildren();
                if (children == null) {
                    children = new ArrayList<>();
                    parent.setChildren(children);
                }
                children.add(item);
            }
        }
        return tree;
    }

    // ==================== 查询与映射 ====================

    private List<UserSearchTreeNodeResp> fetchDepartmentNodes(Condition condition) {
        return dsl().select(longField("id"), intField("pid"), strField("name"))
                .from(HR_DEPARTMENT)
                .where(condition)
                .fetch(
                        record -> {
                            Long id = record.get("id", Long.class);
                            Integer pid = record.get("pid", Integer.class);
                            boolean hasParent = pid != null && pid != 0;
                            return UserSearchTreeNodeResp.builder()
                                    .id(DEPART_ID_PREFIX + id)
                                    .label(record.get("name", String.class))
                                    .pid(hasParent ? DEPART_ID_PREFIX + pid : ROOT_PID)
                                    .type(NODE_TYPE_DEPARTMENT)
                                    .children(new ArrayList<>())
                                    .build();
                        });
    }

    /** 用户查询，列别名与 {@link SysUser} 的人力字段一一对应，可直接 fetchInto */
    private SelectJoinStep<?> selectUsers() {
        return dsl().select(
                        longField("id").as("userId"),
                        strField("user_name").as("userName"),
                        intField("sex").as("sex"),
                        strField("user_avatar").as("userAvatar"),
                        strField("work_number").as("workNumber"),
                        strField("phone").as("phone"),
                        strField("short_name").as("shortName"),
                        longField("subject_id").as("subjectId"),
                        strField("department_id").as("departIds"),
                        strField("department_name").as("departName"),
                        strField("job_name").as("positionName"))
                .from(HR_USER);
    }

    private static Field<Long> longField(String name) {
        return DSL.field(DSL.name(name), Long.class);
    }

    private static Field<Integer> intField(String name) {
        return DSL.field(DSL.name(name), Integer.class);
    }

    private static Field<String> strField(String name) {
        return DSL.field(DSL.name(name), String.class);
    }

    /** 取 hr_manage 物理数据源的 jOOQ 上下文（显式指定，不依赖线程数据源路由） */
    private DSLContext dsl() {
        return dataSourceResolver.getDSLContext(DataSourceConstants.HR_MANAGE);
    }
}
