package com.jdec.platform.common.biz.notify.dto;

import java.util.Map;
import lombok.Data;

/**
 * 推送给前端的配置变更 SSE 事件体
 *
 * <p>由 {@code ConfigChangeMessage} 与 channel 中的 changeType 组合而成。 前端按 {@code changeType}
 * 事件名监听对应的刷新逻辑。
 *
 * @author JDEC Platform
 * @since 2026-11-03
 */
@Data
public class ConfigChangeNotifyEvent {

    /** 变更类型编码（来自 Redis channel 最后一段，如 ROLE_MENU_CHANGED） */
    private String changeType;

    /** 主体ID */
    private Long subjectId;

    /** 项目编码 */
    private String projectNo;

    /** 消息唯一ID */
    private String messageId;

    /** 发布时间 */
    private String publishTime;

    /** 操作人令牌（前端用于刷新本地缓存 / 重新拉取配置） */
    private String token;

    /** 业务 payload（角色ID、受影响用户/主体/模块列表等，由发布侧决定） */
    private Map<String, Object> payload;
}
