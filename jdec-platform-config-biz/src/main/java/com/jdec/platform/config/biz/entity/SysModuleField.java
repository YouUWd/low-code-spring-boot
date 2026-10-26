package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块物理字段定义实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_module_field")
public class SysModuleField {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模块 ID */
    private Long moduleId;

    /** 物理表名 */
    private String tableName;

    /** 物理列名 */
    private String columnName;

    /** 显示名称 * */
    private String displayName;

    /** 业务唯一标识顺序 * */
    private Integer bizKeyOrder;

    /** 显示排序 */
    private Integer sortOrder;

    /** 状态: 1-启用, 0-停用 */
    private Integer status;

    /** 创建人 ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdDate;

    /** 更新人 ID */
    private Long updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedDate;
}
