package com.jdec.platform.dataengine.biz.dto;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.config.api.dto.common.*;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.response.SysFieldResp;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class SysModuleCombineInfo implements Serializable {

    // 模块所在的模块树
    private ModuleTreeDTO moduleTree;

    // 模块信息
    private ModuleInfo moduleInfo;

    // 模块子表
    private Set<String> subTables = new HashSet<>();

    // 模块所有的表
    private Set<String> allTables = new HashSet<>();

    // 模块字段信息 按表名称分组
    private Map<String, Map<String, ModuleFieldDTO>> fieldMapByTable = new HashMap<>();

    // 模块字段信息
    private Map<String, ModuleFieldDTO> fieldAllMap = new HashMap<>();

    // 模块写权限字段
    private Map<String, Set<String>> writeFields = new HashMap<>();

    // 更新权限字段
    private Map<String, Set<String>> updateFields = new HashMap<>();

    // 读权限字段
    private Map<String, Set<String>> readFields = new HashMap<>();

    // 所有字段 按表名分组
    private Map<String, Map<String, SysFieldResp>> allFieldsByTable = new HashMap<>();

    // 所有字段
    private Map<String, SysFieldResp> allFields = new HashMap<>();

    // 唯一名称字段
    private List<String> uniqueNameFields = new ArrayList();

    // 模块相关的表关系
    private ProjectTableRelationsDTO projectTableRelations;

    // 当前模块主表关联的 上级表
    private TableRelationDTO mainTableRelationDTO;

    // 当前模块 子表 关联的 上级表
    private Map<String, TableRelationDTO> subTableRelationMap = new HashMap<>();

    public Set<String> getExternalReferenceFields() {
        if (CollectionUtil.isNotEmpty(this.allFields)) {
            return this.allFields.entrySet().stream()
                    .filter(e -> StringUtils.isNotEmpty(e.getValue().getRelationBusinessNo()))
                    .map(e -> e.getKey())
                    .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }
}
