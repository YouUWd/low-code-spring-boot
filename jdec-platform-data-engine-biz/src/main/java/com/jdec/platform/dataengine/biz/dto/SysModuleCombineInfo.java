package com.jdec.platform.dataengine.biz.dto;

import com.jdec.platform.config.api.dto.common.*;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.response.SysFieldResp;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Data;

@Data
public class SysModuleCombineInfo implements Serializable {

    // 模块所在的模块树
    private ModuleTreeDTO moduleTree;

    // 模块信息
    private ModuleInfo moduleInfo;

    // 模块子表
    private Set<String> subTables;

    // 模块字段信息
    private Map<String, Map<String, ModuleFieldDTO>> fieldMap = new HashMap<>();

    // 模块写权限字段
    private Map<String, Set<String>> writeFields = new HashMap<>();

    // 更新权限字段
    private Map<String, Set<String>> updateFields = new HashMap<>();

    // 读权限字段
    private Map<String, Set<String>> readFields = new HashMap<>();

    // 所有字段
    private Map<String, Map<String, SysFieldResp>> allFields = new HashMap<>();

    // 模块相关的表关系
    private ProjectTableRelationsDTO projectTableRelations;

    // 当前模块主表关联的 上级表
    private TableRelationDTO mainTableRelationDTO;

    // 当前模块 子表 关联的 上级表
    private Map<String, TableRelationDTO> subTableRelationMap = new HashMap<>();
}
