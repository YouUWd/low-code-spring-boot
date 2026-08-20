package com.jdec.platform.shared.utils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jdec.platform.shared.model.PageResult;

/** 分页结果转换工具 */
public class PageResultUtils {

    /** MyBatis-Plus IPage 转 PageResult */
    public static <T> PageResult<T> of(IPage<T> page) {
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords());
    }

    private PageResultUtils() {}
}
