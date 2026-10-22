package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 业务文件存储记录表 (对应各业务库中的 business_file 表) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("business_file")
@Schema(description = "业务文件存储记录")
public class BusinessFile implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属主体ID")
    private Long subjectId;

    @Schema(description = "所属项目/应用编码")
    private String projectNo;

    @Schema(description = "关联菜单ID")
    private Long menuId;

    @Schema(description = "存储文件名 (UUID + 后缀)")
    private String fileName;

    @Schema(description = "原始主文件名 (不含后缀)")
    private String originalName;

    @Schema(description = "文件后缀 (如 pdf, png, docx)")
    private String fileExt;

    @Schema(description = "文件大小 (字节)")
    private Long fileSize;

    @Schema(description = "MIME类型")
    private String contentType;

    @Schema(description = "OSS存储桶名称")
    private String bucketName;

    @Schema(description = "OSS对象相对路径")
    private String objectKey;

    @Schema(description = "创建人ID")
    private Long createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

    @Schema(description = "更新人ID")
    private Long updatedBy;

    @Schema(description = "更新时间")
    private LocalDateTime updatedDate;

    @TableLogic
    @Schema(description = "是否删除: 0-未删除, 1-已删除")
    private Integer deleted;
}
