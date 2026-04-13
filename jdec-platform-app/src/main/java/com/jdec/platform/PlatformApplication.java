package com.jdec.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * JDEC 企业级模块化单体平台启动类。
 *
 * <p>Spring Modulith 会自动将本类所在包 {@code com.jdec.platform} 的 直接子包识别为独立的应用模块（如 auth、config、hr 等）。
 *
 * <p>排除 {@link DataSourceAutoConfiguration}，因为我们使用自定义的 {@code DynamicDataSourceConfig} 来管理多数据源。
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
