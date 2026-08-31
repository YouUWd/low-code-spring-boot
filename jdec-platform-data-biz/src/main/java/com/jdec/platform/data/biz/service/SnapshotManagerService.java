package com.jdec.platform.data.biz.service;

import com.alibaba.fastjson2.JSON;
import com.jdec.platform.data.api.DataSnapshotApi;
import com.jdec.platform.data.api.dto.request.DynamicSnapshotTriggerReq;
import com.jdec.platform.data.api.dto.response.DataSnapshotResp;
import com.jdec.platform.data.api.dto.response.VersionDiffResp;
import com.jdec.platform.data.biz.dsl.JooqContextFactory;
import com.jdec.platform.data.biz.snapshot.SnapshotDiffEngine;
import com.jdec.platform.shared.context.AppContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 业务库全局快照与多版本管理服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotManagerService implements DataSnapshotApi {

    private final DynamicQueryService dynamicQueryService;
    private final JooqContextFactory jooqContextFactory;
    private final SnapshotDiffEngine snapshotDiffEngine;

    private static final String SNAPSHOT_TABLE = "data_engine_snapshot";

    /** 确保当前业务库存在 data_engine_snapshot 表 */
    private void ensureSnapshotTable(DSLContext dsl) {
        String ddl =
                """
            CREATE TABLE IF NOT EXISTS `data_engine_snapshot` (
              `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '快照主键ID',
              `module_id` BIGINT NOT NULL COMMENT '所属 SysModule ID',
              `data_id` BIGINT NOT NULL COMMENT '业务主表主键 ID',
              `version_no` INT NOT NULL DEFAULT 1 COMMENT '版本号',
              `business_no` VARCHAR(64) NULL DEFAULT NULL COMMENT '业务单据编号',
              `final_status_value` INT NOT NULL COMMENT '终结状态值: 99-生效, -99-作废',
              `json_data` LONGTEXT NOT NULL COMMENT '主子表完整 JSON 快照',
              `change_diff` TEXT NULL COMMENT '字段级变更差异 JSON',
              `remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '归档备注',
              `created_by` BIGINT NULL DEFAULT NULL COMMENT '操作人ID',
              `created_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '操作人姓名',
              `created_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
              PRIMARY KEY (`id`) USING BTREE,
              UNIQUE INDEX `uk_module_data_version` (`module_id`, `data_id`, `version_no`) USING BTREE,
              INDEX `idx_data_id` (`data_id`) USING BTREE
            ) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '数据引擎-业务聚合多版本快照表';
            """;
        dsl.execute(ddl);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long triggerSnapshot(DynamicSnapshotTriggerReq req) {
        Long moduleId = req.getModuleId();
        Long dataId = req.getDataId();

        DSLContext dsl = jooqContextFactory.getContext();
        ensureSnapshotTable(dsl);

        // 1. 查询当前聚合实体完整数据并序列化为 JSON
        var detailResp =
                dynamicQueryService.getDetail(
                        com.jdec.platform.data.api.dto.request.DynamicDetailReq.builder()
                                .moduleId(moduleId)
                                .id(dataId)
                                .build());
        String currentSnapshotJson = JSON.toJSONString(detailResp.getData());

        // 2. 查询历史最新快照以计算版本号及 Diff
        Record latestRecord =
                dsl.select(DSL.asterisk())
                        .from(DSL.table(DSL.name(SNAPSHOT_TABLE)))
                        .where(
                                DSL.field(DSL.name("module_id"))
                                        .eq(moduleId)
                                        .and(DSL.field(DSL.name("data_id")).eq(dataId)))
                        .orderBy(DSL.field(DSL.name("version_no")).desc())
                        .limit(1)
                        .fetchOne();

        int newVersion = 1;
        String changeDiff = null;
        if (latestRecord != null) {
            Integer lastVersion = latestRecord.get("version_no", Integer.class);
            newVersion = (lastVersion != null ? lastVersion : 0) + 1;
            String oldJson = latestRecord.get("json_data", String.class);
            changeDiff = snapshotDiffEngine.diffToJson(oldJson, currentSnapshotJson);
        } else {
            changeDiff = snapshotDiffEngine.diffToJson(null, currentSnapshotJson);
        }

        // 3. 插入业务库快照表
        Record inserted =
                dsl.insertInto(DSL.table(DSL.name(SNAPSHOT_TABLE)))
                        .set(DSL.field(DSL.name("module_id")), moduleId)
                        .set(DSL.field(DSL.name("data_id")), dataId)
                        .set(DSL.field(DSL.name("version_no")), newVersion)
                        .set(DSL.field(DSL.name("business_no")), req.getBusinessNo())
                        .set(DSL.field(DSL.name("final_status_value")), req.getFinalStatusValue())
                        .set(DSL.field(DSL.name("json_data")), currentSnapshotJson)
                        .set(DSL.field(DSL.name("change_diff")), changeDiff)
                        .set(DSL.field(DSL.name("remark")), req.getRemark())
                        .set(DSL.field(DSL.name("created_by")), AppContext.getUserId())
                        .returning(DSL.field(DSL.name("id")))
                        .fetchOne();

        Long snapshotId = inserted != null ? inserted.get("id", Long.class) : null;
        log.info(
                "生成业务库多版本快照成功: moduleId={}, dataId={}, version={}, snapshotId={}",
                moduleId,
                dataId,
                newVersion,
                snapshotId);
        return snapshotId;
    }

    @Override
    public List<DataSnapshotResp> getSnapshots(Long moduleId, Long dataId) {
        DSLContext dsl = jooqContextFactory.getContext();
        ensureSnapshotTable(dsl);

        Result<Record> records =
                dsl.select(DSL.asterisk())
                        .from(DSL.table(DSL.name(SNAPSHOT_TABLE)))
                        .where(
                                DSL.field(DSL.name("module_id"))
                                        .eq(moduleId)
                                        .and(DSL.field(DSL.name("data_id")).eq(dataId)))
                        .orderBy(DSL.field(DSL.name("version_no")).desc())
                        .fetch();

        List<DataSnapshotResp> list = new ArrayList<>();
        for (Record r : records) {
            list.add(
                    DataSnapshotResp.builder()
                            .id(r.get("id", Long.class))
                            .moduleId(r.get("module_id", Long.class))
                            .dataId(r.get("data_id", Long.class))
                            .versionNo(r.get("version_no", Integer.class))
                            .businessNo(r.get("business_no", String.class))
                            .finalStatusValue(r.get("final_status_value", Integer.class))
                            .jsonData(r.get("json_data", String.class))
                            .changeDiff(r.get("change_diff", String.class))
                            .remark(r.get("remark", String.class))
                            .createdBy(r.get("created_by", Long.class))
                            .createdName(r.get("created_name", String.class))
                            .build());
        }
        return list;
    }

    @Override
    public VersionDiffResp diff(
            Long moduleId, Long dataId, Integer fromVersion, Integer toVersion) {
        DSLContext dsl = jooqContextFactory.getContext();
        ensureSnapshotTable(dsl);

        Record fromRecord =
                dsl.select(DSL.asterisk())
                        .from(DSL.table(DSL.name(SNAPSHOT_TABLE)))
                        .where(
                                DSL.field(DSL.name("module_id"))
                                        .eq(moduleId)
                                        .and(DSL.field(DSL.name("data_id")).eq(dataId))
                                        .and(DSL.field(DSL.name("version_no")).eq(fromVersion)))
                        .fetchOne();

        Record toRecord =
                dsl.select(DSL.asterisk())
                        .from(DSL.table(DSL.name(SNAPSHOT_TABLE)))
                        .where(
                                DSL.field(DSL.name("module_id"))
                                        .eq(moduleId)
                                        .and(DSL.field(DSL.name("data_id")).eq(dataId))
                                        .and(DSL.field(DSL.name("version_no")).eq(toVersion)))
                        .fetchOne();

        String fromJson = fromRecord != null ? fromRecord.get("json_data", String.class) : null;
        String toJson = toRecord != null ? toRecord.get("json_data", String.class) : null;

        List<Map<String, Object>> diffList = snapshotDiffEngine.diff(fromJson, toJson);

        return VersionDiffResp.builder()
                .moduleId(moduleId)
                .dataId(dataId)
                .fromVersion(fromVersion)
                .toVersion(toVersion)
                .diffList(diffList)
                .build();
    }
}
