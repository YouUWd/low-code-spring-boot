package com.jdec.platform.dataengine.biz.dto.context;

import com.jdec.platform.dataengine.biz.dto.BasicSaveModel;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
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

    private Long structureRootModuleId; // 结构根模块ID

    // 当前模块的完整信息
    private SysModuleCombineInfo sysModuleCompleteResp;

    // 模块缓存
    private Map<Long, SysModuleCombineInfo> moduleCacheMap;

    // 表名
    private String tableName;

    // 外键
    private String tableFk;

    // 模块主表
    private Boolean isModuleMainTable;

    // 关联的上级表
    private String parentTable;

    // 父模块ID
    private Long parentModuleId;

    // 父模块主表ID
    private Long parentModuleMainTableDataId;

    // 表记录
    private Map<String, Object> record;

    // 从数据库读出的记录
    private Map<String, Object> readOutRecord;

    // 需要提前生成的 一些业务字段 businessNo  businessStatus
    private Map<String, Object> bizDataMap;

    // 上一级主表 关联字段 数据
    private Map<String, Object> parentLevelTableAssociationDataMap;

    // 表默认填充的字段,用户上下文数据，主题,操作用户，操作时间等
    private Map<String, Object> defaultUserContextDataMap;

    // 默认 排除的字段
    private Set<String> defaultExcludeFields;

    private Boolean isInsert;

    private Boolean isApproval;

    // 模块定义上 跟模块主表
    private Boolean isRootModuleMainTable;
}
