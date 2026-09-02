package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import java.util.List;

/** 全局物理表关联关系 API */
public interface SysTableRelationApi {

    /** 查询指定项目的全部有效物理表关联 */
    List<TableRelationDTO> listRelations(String projectNo, Long subjectId);

    /** 保存或更新表关联 */
    Long saveRelation(String projectNo, Long subjectId, TableRelationDTO dto);

    /** 删除表关联 */
    void deleteRelation(Long id);
}
