package com.jdec.platform.config.biz.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模块操作日志
 *
 * <p>对应文档中的每个模块的操作记录，如角色配置、菜单配置等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleAction {

    /** 模块名称（如：角色配置、菜单配置、状态配置） */
    private String name;

    /** 模块ID（暂时为空） */
    private String mId;

    /** 操作详情（新增a、修改u、删除d） */
    private ActionDetail actions;
}
