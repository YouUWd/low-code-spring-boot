package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 业务类型字典表 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_business_type")
public class SysBusinessType {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务类型名称 */
    private String businessTypeName;

    /** 业务类型标识 */
    private String businessTypeCode;

    /** 搜索类型（JSON数组，每项含 code/name/component） */
    private String searchType;

    /** 备注 */
    private String remark;

    /** 创建人ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdDate;

    /** 更新人ID */
    private Long updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedDate;

    /** 是否删除: 0-否, 1-是 */
    private Integer deleted;
}
