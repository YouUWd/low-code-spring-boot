package com.jdec.platform.dataengine.biz.operatelog.service;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/** 保存操作日志上下文：{@code prepare} 阶段产出，供保存成功后的 {@code finish} 阶段使用。 */
@Data
@Builder
public class SaveOperateLogContext {

    /** 用户正在操作的模块ID（结构根，可能不是根模块） */
    private Long structureRootModuleId;

    /** 菜单ID（取变更项名称用） */
    private Long menuId;

    /** 模块元数据 */
    private Map<Long, SysModuleCombineInfo> moduleCacheMap;

    /** 保存前的旧数据（详情查询结果），纯新增时为 null */
    private NestedModuleDataDTO oldData;
}
