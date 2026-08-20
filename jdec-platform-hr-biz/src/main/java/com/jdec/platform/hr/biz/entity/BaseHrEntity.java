package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** HR 模块基础实体类。 用于那些有自己的 created_date/updated_date 字段的表，不使用 BaseEntity 的 createdAt/updatedAt。 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseHrEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
}
