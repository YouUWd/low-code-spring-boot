package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 系统表-数据快照表 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_data_snapshot")
@Schema(description = "数据快照")
public class SysDataSnapshot implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "表名")
    private String tableName;

    @Schema(description = "数据ID")
    private Long dataId;

    @Schema(description = "JSON数据快照")
    private String jsonData;

    @Schema(description = "版本号（乐观锁）")
    @Version
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

    @Schema(description = "更新时间")
    private LocalDateTime updatedDate;
}
