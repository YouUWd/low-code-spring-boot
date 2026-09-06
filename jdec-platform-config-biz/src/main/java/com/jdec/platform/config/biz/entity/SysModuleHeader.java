package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块列表表头与检索展示配置实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_module_header")
public class SysModuleHeader {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属模块 ID */
    private Long moduleId;

    /** 字段来源业务模块 ID (跨模块引用时标识原始业务上下文) */
    private Long sourceModuleId;

    /** 物理表名 */
    private String tableName;

    /** 物理列名 */
    private String columnName;

    /** 表头显示名称 */
    private String headerName;

    /** 列宽度 (像素) */
    private Integer width;

    /** 表头列显示排序 */
    private Integer sortOrder;

    /** 搜索类型: input, singleFuzzySelect, multipleSelect, dateRange 等 */
    private String searchType;

    /** 固定列位置: none, left, right */
    private String fixed;

    /** 文本超出是否省略: 0-否, 1-是 */
    private Integer ellipsis;

    /** 是否可排序: 0-不可排序, 1-可排序 */
    private Integer sortable;

    /** 创建人 ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdDate;

    /** 更新人 ID */
    private Long updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedDate;

    /** 逻辑删除: 0-未删除, 1-已删除 */
    @TableLogic private Integer deleted;
}
