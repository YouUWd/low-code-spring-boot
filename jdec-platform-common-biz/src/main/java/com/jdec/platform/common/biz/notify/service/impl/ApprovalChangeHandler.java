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
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 审批流程更新（审批链新增、提交、通过、驳回、取消）配置变更处理
 *
 * <p>对标文档中的 {@code ApprovalChainChangeHandler}：消费 {@link ChangeType#APPROVAL_UPDATE} （channel 末尾 =
 * {@code APPROVAL_CHAIN_CHANGE}）消息，处理要点：
 *
 * <ul>
 *   <li>入参 payload：{@code businessNumber / moduleId / recordId / applicantId}
 *   <li>出参通知 payload：去掉 {@code applicantId}，将 {@code moduleId} 重构为 {@code
 *       affectedModuleIds}（前端据此「哪些模块、哪条业务单的审批状态变了」做局部刷新）
 *   <li><b>排除申请人本人</b>（{@code getAffectedClientSessionListWithoutCreateUser}），避免自己操作触发自己的刷新
 *   <li>推送前 token 置空，防止敏感信息下发到浏览器（基类 {@code buildEvent} 已处理）
 * </ul>
 *
 * @author JDEC Platform
 * @since 2026-11-03
 */
@Slf4j
@Component
public class ApprovalChangeHandler extends AbstractPermissionChangeHandler {

    @Override
    protected Set<ChangeType> supportedTypes() {
        return Set.of(ChangeType.APPROVAL_UPDATE);
    }

    @Override
    public void handle(ConfigChangeMessage message, ChangeType changeType, Long channelSubjectId) {
        Map<String, Object> payload = message.getPayload();
        // 必要字段缺失则不推送（与文档一致）
        if (payload == null
                || payload.get("moduleId") == null
                || payload.get("businessNumber") == null) {
            log.warn("审批链变更消息 payload 必要字段缺失，跳过推送: {}", payload);
            return;
        }

        Long applicantId = toLong(payload.get("applicantId"));
        Long moduleId = toLong(payload.get("moduleId"));
        Object businessNumber = payload.get("businessNumber");
        Object recordId = payload.get("recordId");

        // 取「除申请人本人以外」的所有前端会话（含模拟登录视角兼容）
        List<ClientSession> sessions = getAffectedClientSessionListWithoutCreateUser(applicantId);

        // 收敛到同一主体，避免跨租户把审批变更推给无关主体用户
        if (channelSubjectId != null) {
            sessions =
                    sessions.stream()
                            .filter(s -> channelSubjectId.equals(s.getSubjectId()))
                            .collect(Collectors.toList());
        }

        // 构建通知用 payload：去掉 applicantId，改用受影响的模块集合
        Map<String, Object> notifyPayload = new HashMap<>();
        notifyPayload.put("businessNumber", businessNumber);
        notifyPayload.put("recordId", recordId);
        notifyPayload.put("affectedModuleIds", List.of(moduleId));

        // 复用消息元数据，仅替换 payload（token 由 buildEvent 置空）
        ConfigChangeMessage notifyMessage = new ConfigChangeMessage();
        notifyMessage.setMessageId(message.getMessageId());
        notifyMessage.setSubjectId(message.getSubjectId());
        notifyMessage.setProjectNo(message.getProjectNo());
        notifyMessage.setPublishTime(message.getPublishTime());
        notifyMessage.setToken(null);
        notifyMessage.setPayload(notifyPayload);

        ConfigChangeNotifyEvent event = buildEvent(notifyMessage, changeType);
        log.info(
                "审批链变更 {} 匹配到 {} 个 SSE 会话（已排除申请人 {}），准备推送",
                changeType.getCode(),
                sessions.size(),
                applicantId);
        sendMessage(sessions, event);
    }
}
