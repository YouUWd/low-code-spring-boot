package com.jdec.platform.common.biz.notify.service.impl;

import com.jdec.platform.common.biz.notify.dto.ConfigChangeNotifyEvent;
import com.jdec.platform.common.biz.notify.model.ClientSession;
import com.jdec.platform.common.biz.notify.service.AbstractPermissionChangeHandler;
import com.jdec.platform.shared.dto.ConfigChangeMessage;
import com.jdec.platform.shared.enums.ChangeType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 角色数据权限变更配置变更处理
 *
 * <p>消费 {@link ChangeType#ROLE_DATA_PERMISSION_CHANGED} 消息。生产侧已根据数据权限节点编码「表名*字段名」 反查 {@code
 * sys_module_field} 得到模块ID，再沿 {@code sys_module} 父链找到<b>最顶层父类</b>模块， 将其 moduleId 作为 {@code
 * affectedModuleIds} 写入推送上下文， 本 Handler 显式将其下发给前端，用于「模块级局部刷新」。
 */
@Slf4j
@Component
public class RoleDataPermissionChangeHandler extends AbstractPermissionChangeHandler {

    @Override
    protected Set<ChangeType> supportedTypes() {
        return Set.of(ChangeType.ROLE_DATA_PERMISSION_CHANGED);
    }

    @Override
    public void handle(ConfigChangeMessage message, ChangeType changeType, Long channelSubjectId) {
        Map<String, Object> payload = message.getPayload();

        // 受影响模块ID集合（核心：前端据此做模块级局部刷新）
        Set<Long> moduleIds = toLongSet(payload == null ? null : payload.get("affectedModuleIds"));

        // 解析角色/用户/主体维度，匹配在线会话
        Long roleId = toLong(payload == null ? null : payload.get("roleId"));
        Set<Long> roleIds = toLongSet(payload == null ? null : payload.get("affectedRoleIds"));
        if (roleIds.isEmpty() && roleId != null) {
            roleIds = Set.of(roleId);
        }
        Set<Long> userIds = toLongSet(payload == null ? null : payload.get("affectedUserIds"));
        Set<Long> subjectIds =
                toLongSet(payload == null ? null : payload.get("affectedSubjectIds"));

        List<ClientSession> sessions =
                resolveSessions(roleIds, userIds, subjectIds, channelSubjectId);

        // 构建通知 payload：前端依据受影响维度仅刷新相关模块/角色/用户，避免整页刷新
        Map<String, Object> notifyPayload = new HashMap<>();
        notifyPayload.put("changeType", changeType.getCode());
        if (roleId != null) {
            notifyPayload.put("roleId", roleId);
        }
        notifyPayload.put("affectedRoleIds", List.copyOf(roleIds));
        notifyPayload.put("affectedUserIds", List.copyOf(userIds));
        notifyPayload.put("affectedSubjectIds", List.copyOf(subjectIds));
        notifyPayload.put("affectedModuleIds", List.copyOf(moduleIds));

        ConfigChangeMessage notifyMessage = new ConfigChangeMessage();
        notifyMessage.setMessageId(message.getMessageId());
        notifyMessage.setSubjectId(message.getSubjectId());
        notifyMessage.setProjectNo(message.getProjectNo());
        notifyMessage.setPublishTime(message.getPublishTime());
        notifyMessage.setToken(null);
        notifyMessage.setPayload(notifyPayload);

        ConfigChangeNotifyEvent event = buildEvent(notifyMessage, changeType);
        log.info(
                "角色数据权限变更 {} 匹配到 {} 个 SSE 会话，受影响模块 {} 个",
                changeType.getCode(),
                sessions.size(),
                moduleIds.size());
        sendMessage(sessions, event);
    }
}
