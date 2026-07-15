package com.jdec.platform;

import java.net.InetAddress;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * JDEC 企业级模块化单体平台启动类。
 *
 * <p>Spring Modulith 会自动将本类所在包 {@code com.jdec.platform} 的 直接子包识别为独立的应用模块（如 auth、config、hr 等）。
 *
 * <p>排除 {@link DataSourceAutoConfiguration}，因为我们使用自定义的 {@code DynamicDataSourceConfig} 来管理多数据源。
 */
@EnableFeignClients
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class PlatformApplication {

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext context =
                SpringApplication.run(PlatformApplication.class, args);
        Environment env = context.getEnvironment();
        String port = env.getProperty("server.port", "8080");
        String ip = InetAddress.getLocalHost().getHostAddress();
        System.out.println(
                "\n----------------------------------------------------------\n"
                        + " JDEC Platform 启动成功!\n"
                        + "----------------------------------------------------------\n"
                        + " 接口地址:  http://"
                        + ip
                        + ":"
                        + port
                        + "\n"
                        + " 接口文档:  http://"
                        + ip
                        + ":"
                        + port
                        + "/doc.html\n"
                        + "----------------------------------------------------------");
    }
}
