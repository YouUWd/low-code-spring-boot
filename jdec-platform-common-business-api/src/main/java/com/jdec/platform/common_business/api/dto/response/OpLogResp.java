package com.jdec.platform.common_business.api.dto.response;

import com.jdec.platform.common_business.api.dto.common.OperationChangeDTO;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class OpLogResp implements Serializable {

    /** 自增ID */
    private Long id;

    /** 数据ID */
    private Long dataId;

    /** 用户的主题ID */
    private Long subjectId;

    /** 模块ID */
    private Long moduleId;

    /** 模块标识符 */
    private String moduleSlug;

    /** 模块名称 */
    private String moduleName;

    /** 变更字段内容 */
    private OperationChangeDTO changeContent;

    /** 模拟用户ID */
    private Long mockUserId;

    /** 模拟用户名称 */
    private String mockUserName;

    /** 登录用户ID */
    private Long realUserId;

    /** 登录用户名称 */
    private String realUserName;

    /** 创建时间 */
    private LocalDateTime createdDate;
}
