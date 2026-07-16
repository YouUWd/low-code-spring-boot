package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.config.api.SysDataSnapshotApi;
import com.jdec.platform.config.biz.entity.SysDataSnapshot;
import com.jdec.platform.config.biz.mapper.SysDataSnapshotMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 数据快照 Service */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysDataSnapshotService extends ServiceImpl<SysDataSnapshotMapper, SysDataSnapshot>
        implements SysDataSnapshotApi {

    /**
     * 获取最新快照
     *
     * @param tableName 表名
     * @param dataId 数据ID
     * @return 最新快照，如果不存在返回null
     */
    public SysDataSnapshot getLatestSnapshot(String tableName, Long dataId) {
        return this.getOne(
                new LambdaQueryWrapper<SysDataSnapshot>()
                        .eq(SysDataSnapshot::getTableName, tableName)
                        .eq(SysDataSnapshot::getDataId, dataId)
                        .orderByDesc(SysDataSnapshot::getUpdatedDate)
                        .last("LIMIT 1"));
    }

    /**
     * 保存快照
     *
     * @param tableName 表名
     * @param dataId 数据ID
     * @param jsonData JSON数据
     */
    public void saveSnapshot(String tableName, Long dataId, String jsonData) {
        SysDataSnapshot snapshot =
                SysDataSnapshot.builder()
                        .tableName(tableName)
                        .dataId(dataId)
                        .jsonData(jsonData)
                        .version(1)
                        .build();
        this.save(snapshot);
    }

    /**
     * 使用乐观锁更新快照
     *
     * @param snapshot 快照对象（必须包含version）
     * @return 是否更新成功
     */
    public boolean updateSnapshotWithVersion(SysDataSnapshot snapshot) {
        return this.updateById(snapshot);
    }
}
