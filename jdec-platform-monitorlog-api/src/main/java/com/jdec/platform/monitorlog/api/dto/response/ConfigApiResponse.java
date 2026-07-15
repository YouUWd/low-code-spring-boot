package com.jdec.platform.monitorlog.api.dto.response;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfigApiResponse<T> implements Serializable {

    private int status;
    private String msg;
    private T data;
    private String errorType;

    public static Boolean isSuccessResponse(ConfigApiResponse response) {
        return response != null && response.getStatus() == ApiResponseCodeEnum.OK.getCode();
    }
}
