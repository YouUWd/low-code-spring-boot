package com.jdec.platform.common.biz.constant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/** 国民经济行业分类数据常量持有类 */
@Slf4j
public final class IndustryConstants {

    private IndustryConstants() {}

    /** 完整行业分类树结构常量（类加载时解析一次，全局只读） */
    public static final List<Map<String, Object>> INDUSTRY_TREE = initIndustryTree();

    private static List<Map<String, Object>> initIndustryTree() {
        try (InputStream is = openIndustryStream()) {
            if (is != null) {
                List<Map<String, Object>> tree =
                        new ObjectMapper()
                                .readValue(is, new TypeReference<List<Map<String, Object>>>() {});
                if (tree != null) {
                    return Collections.unmodifiableList(tree);
                }
            } else {
                log.warn("未找到 industry.json 行业配置数据，INDUSTRY_TREE 将为空");
            }
        } catch (Exception ex) {
            log.error("加载 industry.json 行业配置数据失败: err={}", ex.getMessage(), ex);
        }
        return Collections.emptyList();
    }

    private static InputStream openIndustryStream() {
        // 1. 优先从类路径读取（在主模块 jdec-platform-app 运行或打包后，industry.json 位于根 Classpath）
        InputStream is = IndustryConstants.class.getResourceAsStream("/industry.json");
        if (is != null) {
            return is;
        }
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl != null) {
            is = cl.getResourceAsStream("industry.json");
            if (is != null) {
                return is;
            }
        }
        // 2. 本地开发/单模块测试兜底：在 common-biz 独立执行单测时，自动从主模块源码路径读取
        String[] fallbackPaths = {
            "../jdec-platform-app/src/main/resources/industry.json",
            "jdec-platform-app/src/main/resources/industry.json"
        };
        for (String path : fallbackPaths) {
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                try {
                    return new FileInputStream(file);
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}
