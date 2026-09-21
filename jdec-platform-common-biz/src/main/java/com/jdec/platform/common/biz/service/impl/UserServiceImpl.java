package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.common.biz.service.UserService;
import com.jdec.platform.config.api.SysRoleApi;
import com.jdec.platform.config.api.dto.response.UserSearchTreeNodeResp;
import com.jdec.platform.config.api.dto.response.UserSwitchDataResp;
import com.jdec.platform.hr.api.DepartmentApi;
import com.jdec.platform.hr.api.bo.DepartmentBO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户公共能力实现。
 *
 * <p>迁移自源项目 {@code com.jdec.student_manage.business.biz.service.impl.UserServiceImpl}。
 *
 * <p><b>关于数据来源：</b>源实现通过 {@code ConfigCenterApiService} → {@code ConfigCenterApiClient}（HTTP
 * 远程调用配置中心）取数；平台是模块化单体，配置中心就在进程内，因此按平台既有约定（见 {@code data-biz}）直接注入 {@code config-api} 的接口，不再保留
 * {@code ConfigCenterApiService} 这层只用于拆 {@code ConfigApiResponse} 包装的薄封装。
 *
 * <p><b>只使用 {@code SysRoleApi} 的既有方法：</b>人员架构树由本类自行组装 —— 用户数据取既有的 {@link
 * SysRoleApi#getUserSwitchData(String)}，部门层级取 {@link
 * DepartmentApi#getDepartmentList()}，再在本地建树并修剪空部门。之所以不把「查用户 + 查部门 + 建树」整体收进 {@code
 * SysRoleApi}：{@code config-api} 是跨模块契约，不应为单个消费方扩接口。代价是本类的建树逻辑与 {@code config-biz} 的 {@code
 * SysRoleController#getUserSwitchTree} 端点存在重复；若再出现第三个消费方，应抽成公共工具而非继续复制。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 节点类型：用户（部门节点为 1） */
    private static final Integer NODE_TYPE_USER = 3;

    /** 根节点的父ID */
    private static final String ROOT_PID = "D0";

    /** 部门ID前缀 */
    private static final String DEPART_ID_PREFIX = "D";

    /** 用户ID前缀 */
    private static final String USER_ID_PREFIX = "U";

    private final SysRoleApi sysRoleApi;
    private final DepartmentApi departmentApi;

    /**
     * 获取人员架构树 - 对应 PHP UserUtils::getUserTreeList
     *
     * <p>源实现的 {@code withExistsRole}（即 {@code userType == 0}）分支原本会做「是否有模拟登录权限」的校验，但该私有方法在源里是 {@code
     * return true} 的桩，等于不校验；此处按用户确认**忠实保留**该行为，不引入真实的特殊权限校验。
     *
     * <p>主体不再由方法参数传入：源项目由 Controller 的 {@code Subject-Id} 请求头取值，平台改由 {@code AppContext} 统一从 {@code
     * X-Subject-Id} 读取（见 {@code AppContextInterceptor}），{@code getUserSwitchData} 内部自行取用。
     */
    @Override
    public List<UserSearchTreeNodeResp> getUserTreeList(Integer userType, String userName) {
        // userType: 0=全部, 1=内部, 2=外部
        boolean withExistsRole = Integer.valueOf(0).equals(userType);

        // 权限检查：验证是否有模拟登录权限（源实现恒为 true，即不拦截）
        if (withExistsRole && !hasSimulateLoginPermission()) {
            return Collections.emptyList();
        }

        // 当前项目仅支持内部用户树，外部用户（userType=2）直接返回空列表
        if (Integer.valueOf(2).equals(userType)) {
            return Collections.emptyList();
        }

        // 1. 用户数据：走 SysRoleApi 既有方法（sys_user 已在角色保存时同步主体下用户，无需再调 HR 取用户）
        UserSwitchDataResp data = sysRoleApi.getUserSwitchData(userName);
        List<UserSwitchDataResp.SysUserInfo> sysUsers =
                (data == null || data.getSysUsers() == null)
                        ? Collections.emptyList()
                        : data.getSysUsers();

        // 2. 用户节点按 userId 去重后，再按所属部门分桶
        Map<String, List<UserSearchTreeNodeResp>> usersByDepartId = new LinkedHashMap<>();
        for (UserSearchTreeNodeResp userNode : buildUserNodes(sysUsers).values()) {
            usersByDepartId
                    .computeIfAbsent(userNode.getDepartId(), key -> new ArrayList<>())
                    .add(userNode);
        }

        // 3. 部门节点（HR 模块）并挂载其下的用户
        List<UserSearchTreeNodeResp> departNodes = new ArrayList<>();
        for (DepartmentBO dept : departmentApi.getDepartmentList()) {
            String pid =
                    (dept.getPid() != null && dept.getPid() != 0)
                            ? DEPART_ID_PREFIX + dept.getPid()
                            : ROOT_PID;
            String departId = DEPART_ID_PREFIX + dept.getId();
            departNodes.add(
                    UserSearchTreeNodeResp.builder()
                            .id(departId)
                            .label(dept.getName())
                            .pid(pid)
                            .type(dept.getType() != null ? dept.getType() : 1)
                            .children(
                                    new ArrayList<>(
                                            usersByDepartId.getOrDefault(
                                                    departId, new ArrayList<>())))
                            .build());
        }

        // 4. 建嵌套树，并修剪掉没有用户的部门
        return filterEmptyDepartments(buildNestedTree(departNodes, ROOT_PID));
    }

    /** 源实现为恒真的桩方法，此处原样保留以便将来补全。 */
    private boolean hasSimulateLoginPermission() {
        return true;
    }

    /**
     * 系统用户 → 用户节点（按 userId 去重）
     *
     * <p>一个用户可能挂在多个部门下（{@code departIds} 为逗号分隔），与原实现一致：逐部门覆盖写入，最终归属最后一个非空部门。
     */
    private Map<Long, UserSearchTreeNodeResp> buildUserNodes(
            List<UserSwitchDataResp.SysUserInfo> sysUsers) {
        Map<Long, UserSearchTreeNodeResp> userMap = new LinkedHashMap<>();
        for (UserSwitchDataResp.SysUserInfo user : sysUsers) {
            String deptIds = user.getDepartIds();
            if (deptIds == null || deptIds.isEmpty()) {
                continue;
            }
            for (String deptId : deptIds.split(",")) {
                deptId = deptId.trim();
                if (deptId.isEmpty()) {
                    continue;
                }
                userMap.put(
                        user.getUserId(),
                        UserSearchTreeNodeResp.builder()
                                .id(USER_ID_PREFIX + user.getUserId())
                                .sysId(user.getId())
                                .label(user.getUserName())
                                .departId(DEPART_ID_PREFIX + deptId)
                                .sex(user.getSex() != null ? user.getSex() : 1)
                                .avatar(user.getUserAvatar())
                                .workNumber(
                                        user.getWorkNumber() != null ? user.getWorkNumber() : "")
                                .type(NODE_TYPE_USER)
                                .departName(
                                        user.getDepartName() != null ? user.getDepartName() : "")
                                .subjectId(user.getSubjectId())
                                .build());
            }
        }
        return userMap;
    }

    /**
     * 构建嵌套树结构
     *
     * @param items 扁平节点列表
     * @param rootPid 根节点父ID
     * @return 树形结构
     */
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
            // 父节点缺失时按根节点处理，避免整棵子树被丢掉
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

    /**
     * 过滤掉没有用户的部门节点（枝叶修剪）
     *
     * @param nodes 树形节点列表
     * @return 过滤后的节点列表
     */
    private List<UserSearchTreeNodeResp> filterEmptyDepartments(
            List<UserSearchTreeNodeResp> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return nodes;
        }

        List<UserSearchTreeNodeResp> filtered = new ArrayList<>();
        for (UserSearchTreeNodeResp node : nodes) {
            if (hasUsers(node)) {
                if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                    node.setChildren(filterEmptyDepartments(node.getChildren()));
                }
                filtered.add(node);
            }
        }
        return filtered;
    }

    /**
     * 检查节点是否包含用户
     *
     * @param node 树形节点
     * @return 是否包含用户
     */
    private boolean hasUsers(UserSearchTreeNodeResp node) {
        if (NODE_TYPE_USER.equals(node.getType())) {
            return true;
        }

        List<UserSearchTreeNodeResp> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            return false;
        }
        for (UserSearchTreeNodeResp child : children) {
            if (hasUsers(child)) {
                return true;
            }
        }
        return false;
    }
}
