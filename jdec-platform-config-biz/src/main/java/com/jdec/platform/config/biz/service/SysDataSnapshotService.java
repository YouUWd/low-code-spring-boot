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
@DataSource(DataSourceConstants.CONFIG_ENGINE)
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

    /**
     * 保存或更新快照（新增时创建，更新时更新已有记录）
     *
     * @param tableName 表名
     * @param dataId 数据ID
     * @param jsonData JSON数据
     */
    public void saveOrUpdateSnapshot(String tableName, Long dataId, String jsonData) {
        // 查询是否已存在快照
        SysDataSnapshot existingSnapshot = getLatestSnapshot(tableName, dataId);

        if (existingSnapshot == null) {
            // 不存在，创建新快照
            SysDataSnapshot snapshot =
                    SysDataSnapshot.builder()
                            .tableName(tableName)
                            .dataId(dataId)
                            .jsonData(jsonData)
                            .version(1)
                            .build();
            this.save(snapshot);
            log.info("创建新快照: tableName={}, dataId={}", tableName, dataId);
        } else {
            // 已存在，更新快照数据
            existingSnapshot.setJsonData(jsonData);
            this.updateById(existingSnapshot);
            log.info(
                    "更新快照: tableName={}, dataId={}, snapshotId={}",
                    tableName,
                    dataId,
                    existingSnapshot.getId());
        }
    }

    /**
     * 删除指定表和数据ID的快照
     *
     * @param tableName 表名
     * @param dataId 数据ID
     * @return 删除的快照记录，如果不存在返回null
     */
    public SysDataSnapshot deleteSnapshot(String tableName, Long dataId) {
        SysDataSnapshot snapshot = getLatestSnapshot(tableName, dataId);
        if (snapshot != null) {
            this.removeById(snapshot.getId());
            log.info(
                    "删除快照: tableName={}, dataId={}, snapshotId={}",
                    tableName,
                    dataId,
                    snapshot.getId());
        }
        return snapshot;
    }

    /**
     * 批量删除快照（根据表名和多个数据ID）
     *
     * @param tableName 表名
     * @param dataIds 数据ID列表
     * @return 删除的快照数量
     */
    public int deleteSnapshots(String tableName, java.util.List<Long> dataIds) {
        if (dataIds == null || dataIds.isEmpty()) {
            return 0;
        }

        long deletedCount =
                this.count(
                        new LambdaQueryWrapper<SysDataSnapshot>()
                                .eq(SysDataSnapshot::getTableName, tableName)
                                .in(SysDataSnapshot::getDataId, dataIds));

        if (deletedCount > 0) {
            this.remove(
                    new LambdaQueryWrapper<SysDataSnapshot>()
                            .eq(SysDataSnapshot::getTableName, tableName)
                            .in(SysDataSnapshot::getDataId, dataIds));
            log.info(
                    "批量删除快照: tableName={}, dataIds={}, count={}", tableName, dataIds, deletedCount);
        }

        return (int) deletedCount;
    }

    /**
     * 批量删除快照（根据表名和多个数据ID）- 别名方法
     *
     * @param tableName 表名
     * @param dataIds 数据ID列表
     * @return 删除的快照数量
     */
    public int deleteSnapshotsByDataIds(String tableName, java.util.List<Long> dataIds) {
        return deleteSnapshots(tableName, dataIds);
    }
}
