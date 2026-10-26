package com.jdec.platform.shared.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MyBatis-Plus 全局配置类。 */
@Configuration
public class MybatisPlusConfig implements MetaObjectHandler {

    /** 注册 MyBatis-Plus 拦截器（如分页插件）。 */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加乐观锁拦截器（需在分页插件之前）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 添加 MySQL 分页拦截器
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /** 插入时自动填充 `createdAt` 和 `updatedAt`。 */
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdDate", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedDate", LocalDateTime.class, LocalDateTime.now());
    }

    /** 更新时自动填充 `updatedAt`。 */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedDate", LocalDateTime.class, LocalDateTime.now());
    }
}
