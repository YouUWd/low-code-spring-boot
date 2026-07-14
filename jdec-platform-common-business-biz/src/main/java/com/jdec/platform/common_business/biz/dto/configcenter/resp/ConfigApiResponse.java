package com.jdec.platform.common_business.biz.dto.configcenter.resp;

import com.jdec.platform.common_business.biz.enums.ApiResponseCodeEnum;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ConfigApiResponse<T> implements Serializable {

    private int status;
    private String msg;
    private T data;
    private String errorType;

    public static Boolean isSuccessResponse(ConfigApiResponse response) {
        return response != null && response.getStatus() == ApiResponseCodeEnum.OK.getCode();
    }
}
