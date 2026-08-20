package com.jdec.platform.shared.utils;

import lombok.Getter;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Spring Bean 工具类 用于在非Spring管理的组件中获取Spring容器中的Bean */
@Component
public class SpringBeanUtil implements ApplicationContextAware {

    /** -- GETTER -- 获取ApplicationContext */
    @Getter private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringBeanUtil.applicationContext = applicationContext;
    }

    /**
     * 根据Bean名称获取Bean实例
     *
     * @param name Bean名称
     * @param <T> 返回类型
     * @return Bean实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name) {
        return (T) applicationContext.getBean(name);
    }

    /**
     * 根据Bean名称和类型获取Bean实例
     *
     * @param name Bean名称
     * @param requiredType 返回类型
     * @param <T> 返回类型
     * @return Bean实例
     */
    public static <T> T getBean(String name, Class<T> requiredType) {
        return applicationContext.getBean(name, requiredType);
    }

    /**
     * 根据类型获取Bean实例
     *
     * @param requiredType 类型
     * @param <T> 返回类型
     * @return Bean实例
     */
    public static <T> T getBean(Class<T> requiredType) {
        return applicationContext.getBean(requiredType);
    }

    /**
     * 判断是否包含指定名称的Bean
     *
     * @param name Bean名称
     * @return 是否包含
     */
    public static boolean containsBean(String name) {
        return applicationContext.containsBean(name);
    }

    /**
     * 判断指定名称的Bean是否为单例
     *
     * @param name Bean名称
     * @return 是否为单例
     */
    public static boolean isSingleton(String name) {
        return applicationContext.isSingleton(name);
    }

    /**
     * 获取指定名称Bean的类型
     *
     * @param name Bean名称
     * @return Bean类型
     */
    public static Class<?> getType(String name) {
        return applicationContext.getType(name);
    }

    /**
     * 获取指定类型的所有Bean名称
     *
     * @param type 类型
     * @return Bean名称数组
     */
    public static String[] getBeanNamesForType(Class<?> type) {
        return applicationContext.getBeanNamesForType(type);
    }

    /**
     * 获取Spirngboot环境变量对象
     *
     * @return
     */
    public static Environment getEnvironment() {
        return applicationContext.getEnvironment();
    }
}
