package com.jdec.platform.dataengine.biz.dto.context;

import com.jdec.platform.dataengine.api.dto.model.SysModuleInfo;
import com.jdec.platform.dataengine.biz.dto.BasicSaveModel;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import org.jooq.DSLContext;

@SuperBuilder
@Data
public class RecordSaveContext extends BasicSaveModel {

    private DSLContext dsl;

    // 根模块的ID
    private Long rootModuleId;

    // 根模块对应的主表数据ID
    private Long rootModuleMainTableDataId;

    // 根模块对应的主表数据版本
    private Integer rootModuleMainTableDataVersion;

    // 当前模块的完整信息
    private SysModuleInfo sysModuleCompleteResp;

    // 表名
    private String tableName;

    // 关联的上级表
    private String parentLevelTable;

    // 关联的上级表 数据 ID
    private Long parentLevelTableId;

    // 表记录
    private Map<String, Object> record;

    // 需要提前生成的 一些业务字段 businessNo  businessStatus
    private Map<String, Object> bizDataMap;

    // 上一级主表 关联字段 数据
    private Map<String, Object> parentLevelTableAssociationDataMap;

    // 表默认填充的字段,用户上下文数据，主题,操作用户，操作时间等
    private Map<String, Object> defaultUserContextDataMap;

    // 默认 排除的字段
    private Set<String> defaultExcludeFields;

    private Boolean isInsert;

    private Boolean isRootModuleMainTable;
}
