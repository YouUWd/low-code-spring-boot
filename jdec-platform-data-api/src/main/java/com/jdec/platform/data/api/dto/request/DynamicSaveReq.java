package com.jdec.platform.data.api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jdec.platform.data.api.dto.model.AbstractDynamicData;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "动态主子表保存请求模型 (支持前端原封不动提交详情返回的对象)")
public class DynamicSaveReq extends AbstractDynamicData<DynamicSaveReq> {

    private static final long serialVersionUID = 1L;
}
