package com.jdec.platform.config.biz.listener;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.biz.dto.DataPermissionMappingMessage;
import com.jdec.platform.config.biz.entity.SysDataPermissionMapping;
import com.jdec.platform.config.biz.mapper.SysDataPermissionMappingMapper;
import com.jdec.platform.shared.annotation.RedisTopicListener;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据权限映射 Redis 消息监听器
 *
 * <p>订阅 Redis channel 接收业务系统推送的数据权限映射消息，并同步到数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
@RedisTopicListener(topic = "${redis.data-permission-mapping.channel:data-permission-mapping}")
public class DataPermissionMappingListener implements MessageListener {

    private final SysDataPermissionMappingMapper mappingMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            String channel = new String(message.getChannel());

            log.info("收到数据权限映射消息，channel: {}, body: {}", channel, body);

            // 解析消息
            DataPermissionMappingMessage msg =
                    JSONUtil.toBean(body, DataPermissionMappingMessage.class);

            // 校验消息
            if (!validateMessage(msg)) {
                log.warn("数据权限映射消息校验失败: {}", body);
                return;
            }

            // 处理消息
            handleMessage(msg);

            log.info("数据权限映射消息处理成功，dataCode: {}, operate: {}", msg.getDataCode(), msg.getOperate());
        } catch (Exception e) {
            log.error("处理数据权限映射消息失败", e);
        }
    }

    /** 校验消息 */
    private boolean validateMessage(DataPermissionMappingMessage msg) {
        if (msg == null) {
            return false;
        }
        if (msg.getDataCode() == null || msg.getDataCode().isEmpty()) {
            log.warn("dataCode 不能为空");
            return false;
        }
        if (msg.getSubjectId() == null) {
            log.warn("subjectId 不能为空");
            return false;
        }
        if (msg.getOperate() == null || msg.getOperate().isEmpty()) {
            log.warn("operate 不能为空");
            return false;
        }
        if (!"add".equalsIgnoreCase(msg.getOperate())
                && !"update".equalsIgnoreCase(msg.getOperate())
                && !"delete".equalsIgnoreCase(msg.getOperate())) {
            log.warn("operate 必须为 add/update/delete 之一");
            return false;
        }
        if (msg.getDataList() == null || msg.getDataList().isEmpty()) {
            log.warn("dataList 不能为空");
            return false;
        }
        return true;
    }

    /** 处理消息 */
    @Transactional(rollbackFor = Exception.class)
    public void handleMessage(DataPermissionMappingMessage msg) {
        String operate = msg.getOperate().toLowerCase();

        switch (operate) {
            case "add":
                handleAdd(msg);
                break;
            case "update":
                handleUpdate(msg);
                break;
            case "delete":
                handleDelete(msg);
                break;
            default:
                log.warn("不支持的操作类型: {}", operate);
        }
    }

    /** 处理新增 */
    private void handleAdd(DataPermissionMappingMessage msg) {
        for (DataPermissionMappingMessage.DataItem item : msg.getDataList()) {
            // 检查是否已存在
            LambdaQueryWrapper<SysDataPermissionMapping> wrapper =
                    Wrappers.<SysDataPermissionMapping>lambdaQuery()
                            .eq(SysDataPermissionMapping::getDataCode, msg.getDataCode())
                            .eq(SysDataPermissionMapping::getBizId, item.getBizId())
                            .eq(SysDataPermissionMapping::getSubjectId, msg.getSubjectId());

            if (msg.getProjectNo() != null && !msg.getProjectNo().isEmpty()) {
                wrapper.eq(SysDataPermissionMapping::getProjectNo, msg.getProjectNo());
            }

            boolean exists = mappingMapper.exists(wrapper);
            if (exists) {
                log.warn(
                        "数据权限映射已存在，跳过新增: dataCode={}, bizId={}",
                        msg.getDataCode(),
                        item.getBizId());
                continue;
            }

            // 新增
            SysDataPermissionMapping entity = new SysDataPermissionMapping();
            entity.setDataCode(msg.getDataCode());
            entity.setBizId(item.getBizId());
            entity.setBizName(item.getBizName());
            entity.setSubjectId(msg.getSubjectId());
            entity.setProjectNo(msg.getProjectNo());

            mappingMapper.insert(entity);
            log.info("新增数据权限映射成功: dataCode={}, bizId={}", msg.getDataCode(), item.getBizId());
        }
    }

    /** 处理更新 */
    private void handleUpdate(DataPermissionMappingMessage msg) {
        for (DataPermissionMappingMessage.DataItem item : msg.getDataList()) {
            // 查找已存在的记录
            LambdaQueryWrapper<SysDataPermissionMapping> wrapper =
                    Wrappers.<SysDataPermissionMapping>lambdaQuery()
                            .eq(SysDataPermissionMapping::getDataCode, msg.getDataCode())
                            .eq(SysDataPermissionMapping::getBizId, item.getBizId())
                            .eq(SysDataPermissionMapping::getSubjectId, msg.getSubjectId());

            if (msg.getProjectNo() != null && !msg.getProjectNo().isEmpty()) {
                wrapper.eq(SysDataPermissionMapping::getProjectNo, msg.getProjectNo());
            }

            SysDataPermissionMapping entity = mappingMapper.selectOne(wrapper);
            if (entity == null) {
                log.warn(
                        "数据权限映射不存在，无法更新: dataCode={}, bizId={}",
                        msg.getDataCode(),
                        item.getBizId());
                continue;
            }

            // 更新
            entity.setBizName(item.getBizName());
            mappingMapper.updateById(entity);
            log.info("更新数据权限映射成功: dataCode={}, bizId={}", msg.getDataCode(), item.getBizId());
        }
    }

    /** 处理删除 */
    private void handleDelete(DataPermissionMappingMessage msg) {
        for (DataPermissionMappingMessage.DataItem item : msg.getDataList()) {
            // 删除记录
            LambdaQueryWrapper<SysDataPermissionMapping> wrapper =
                    Wrappers.<SysDataPermissionMapping>lambdaQuery()
                            .eq(SysDataPermissionMapping::getDataCode, msg.getDataCode())
                            .eq(SysDataPermissionMapping::getBizId, item.getBizId())
                            .eq(SysDataPermissionMapping::getSubjectId, msg.getSubjectId());

            if (msg.getProjectNo() != null && !msg.getProjectNo().isEmpty()) {
                wrapper.eq(SysDataPermissionMapping::getProjectNo, msg.getProjectNo());
            }

            int deleted = mappingMapper.delete(wrapper);
            if (deleted > 0) {
                log.info(
                        "删除数据权限映射成功: dataCode={}, bizId={}, 删除数量={}",
                        msg.getDataCode(),
                        item.getBizId(),
                        deleted);
            } else {
                log.warn(
                        "数据权限映射不存在，无法删除: dataCode={}, bizId={}",
                        msg.getDataCode(),
                        item.getBizId());
            }
        }
    }
}
