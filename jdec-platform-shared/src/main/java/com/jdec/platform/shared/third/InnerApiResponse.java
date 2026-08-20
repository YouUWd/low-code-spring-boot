package com.jdec.platform.shared.third;

import lombok.Data;

/**
 * 外部 API 的通用响应包装。
 *
 * @param <T> 响应数据类型
 */
@Data
public class InnerApiResponse<T> {

    /** 状态码（200 表示成功） */
    private int status;

    /** 提示信息 */
    private String msg;

    /** 响应数据 */
    private T data;

    /** 判断是否成功 */
    public boolean isSuccess() {
        return status == 200;
    }
}
