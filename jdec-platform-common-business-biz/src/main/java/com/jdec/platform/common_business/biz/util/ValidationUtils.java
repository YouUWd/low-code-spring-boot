package com.jdec.platform.common_business.biz.util;

import com.jdec.platform.shared.exception.BusinessException;

public class ValidationUtils {

    public static void requirePositive(Long id, String message) {
        if (id == null || id <= 0) {
            throw new BusinessException(400, message);
        }
    }
}
