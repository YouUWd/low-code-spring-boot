package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 租户信息实体
 */
@Data
@TableName("sys_tenant")
public class SysTenant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String companyCode;
    private String companyName;
    private String entityCode;
    private String entityName;
    private String appName;
    private String appCode;
    private String appDesc;
    private String tenantType;
    private String unifiedCreditCode;
    private String legalPerson;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private String address;
    private Integer maxUsers;
    private Integer maxModules;
    private Long storageQuota;
    private LocalDate expireDate;
    private String tenantStatus;
    
    /**
     * 数据库配置 JSON
     */
    private String dbConfig;
    
    /**
     * 功能开关配置 JSON
     */
    private String featureFlags;

    private String createdBy;
    private LocalDateTime createdDate;
    private String createdName;
    private String updatedBy;
    private LocalDateTime updatedDate;
    private String updatedName;
    private Integer deleted;
}
