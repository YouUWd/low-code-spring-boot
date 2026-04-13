package com.jdec.platform.config.biz.registry;

import com.jdec.platform.shared.annotation.ModuleVO;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 模块 VO 扫描器
 *
 * <p>使用 ClassGraph 扫描所有带有 @ModuleVO 注解的类。
 */
@Slf4j
@Component
public class ModuleVOScanner {

    private static final String[] SCAN_PACKAGES = {
        "com.jdec.platform.hr.api", "com.jdec.platform.auth.api", "com.jdec.platform.config.api"
    };

    /**
     * 扫描所有带有 @ModuleVO 注解的类
     *
     * @return 带有 @ModuleVO 注解的类列表
     */
    public List<Class<?>> scanModuleVOClasses() {
        List<Class<?>> result = new ArrayList<>();

        try (ScanResult scanResult =
                new ClassGraph()
                        .acceptPackages(SCAN_PACKAGES)
                        .enableClassInfo()
                        .enableAnnotationInfo()
                        .scan()) {

            log.debug("开始扫描包: {}", String.join(", ", SCAN_PACKAGES));

            // 获取所有带有 @ModuleVO 注解的类
            for (ClassInfo classInfo :
                    scanResult.getClassesWithAnnotation(ModuleVO.class.getName())) {
                try {
                    Class<?> clazz = Class.forName(classInfo.getName());
                    result.add(clazz);
                    log.info("扫描到模块 VO: {}", classInfo.getName());
                } catch (ClassNotFoundException e) {
                    log.warn("无法加载类: {}", classInfo.getName(), e);
                }
            }
        } catch (Exception e) {
            log.error("扫描模块 VO 失败", e);
        }

        log.info("扫描完成，共发现 {} 个模块 VO", result.size());
        return result;
    }
}
