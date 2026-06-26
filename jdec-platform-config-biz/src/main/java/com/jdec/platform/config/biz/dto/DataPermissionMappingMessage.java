package com.jdec.platform.config.biz.dto;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/** Redis 数据权限映射消息 DTO */
@Data
public class DataPermissionMappingMessage implements Serializable {

    /** 数据权限编码 */
    private String dataCode;

    /** 主体ID */
    private Long subjectId;

    /** 项目编码 */
    private String projectNo;

    /** 操作类型：add/update/delete */
    private String operate;

    /** 数据列表 */
    private List<DataItem> dataList;

    /** 数据项 */
    @Data
    public static class DataItem implements Serializable {
        /** 业务ID */
        private Long bizId;

        /** 业务名称 */
        private String bizName;
    }
}
