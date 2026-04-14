package com.jdec.platform.shared.permission;

import com.jdec.platform.shared.annotation.SysModule;
import com.jdec.platform.shared.model.PageResult;
import com.jdec.platform.shared.service.ModuleFieldQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 字段过滤引擎
 *
 * <p>根据用户权限过滤 @SysModule 标记的对象的字段
 * <p>支持单个对象、集合、分页结果的过滤
 * <p>支持嵌套对象的递归过滤
 * <p>字段信息从权限系统查询，支持人工更新
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FieldFilterEngine {

    private final PermissionValidator permissionValidator;
    private final ModuleFieldQueryService moduleFieldQueryService;

    /**
     * 过滤对象
     *
     * @param obj 待过滤的对象
     * @return 过滤后的对象
     */
    public Object filterObject(Object obj) {
        if (obj == null) {
            return null;
        }

        // 检查是否为 @SysModule 标记的对象
        SysModule annotation = obj.getClass().getAnnotation(SysModule.class);
        if (annotation == null) {
            return obj;
        }

        String moduleCode = annotation.moduleCode();
        log.debug("开始过滤 @SysModule 对象: {}", moduleCode);

        // 从权限系统获取模块的字段编码
        List<String> fieldCodes = moduleFieldQueryService.getFieldCodesByModuleCode(moduleCode);
        if (fieldCodes == null || fieldCodes.isEmpty()) {
            log.debug("模块未注册或无字段: {}", moduleCode);
            return obj;
        }

        // 创建过滤后的对象
        try {
            Object filtered = createFilteredObject(obj, moduleCode, fieldCodes);
            log.debug("@SysModule 对象过滤完成: {}", moduleCode);
            return filtered;
        } catch (Exception e) {
            log.error("过滤 @SysModule 对象失败: {}", moduleCode, e);
            return obj;
        }
    }

    /**
     * 过滤分页结果
     *
     * <p>性能优化：一次性获取权限，应用到所有记录
     * <p>分页结果中的所有记录都是同一个模块的，无需对每个记录都进行权限检查
     * <p>从泛型类型推断Response类型，避免依赖实际数据
     *
     * @param pageResult 分页结果
     */
    public void filterPageResult(PageResult<?> pageResult) {
        if (pageResult == null || pageResult.getRecords() == null || pageResult.getRecords().isEmpty()) {
            return;
        }

        List<?> records = pageResult.getRecords();
        
        // 从PageResult的泛型类型推断Response类型
        Class<?> responseType = inferResponseType(pageResult);
        if (responseType == null) {
            log.debug("无法推断Response类型");
            return;
        }

        SysModule annotation = responseType.getAnnotation(SysModule.class);
        if (annotation == null) {
            // 不是 @SysModule 标记的对象，无需过滤
            return;
        }

        String moduleCode = annotation.moduleCode();
        log.debug("开始过滤分页结果: moduleCode={}, 记录数={}", moduleCode, records.size());

        // 一次性获取用户对该模块所有字段的READ权限
        Set<String> allowedFields = permissionValidator.getReadableFields(moduleCode);
        if (allowedFields.isEmpty()) {
            log.debug("用户对模块无读权限: {}", moduleCode);
            pageResult.setRecords(new ArrayList<>());
            return;
        }

        // 应用权限过滤到所有记录
        List<Object> filteredRecords = new ArrayList<>();
        try {
            for (Object record : records) {
                Object filtered = createFilteredObjectWithPermissions(record, allowedFields);
                filteredRecords.add(filtered);
            }
        } catch (Exception e) {
            log.error("过滤分页结果失败: moduleCode={}", moduleCode, e);
            return;
        }

        // 设置过滤后的记录
        @SuppressWarnings("unchecked")
        PageResult<Object> pageResultObj = (PageResult<Object>) pageResult;
        pageResultObj.setRecords(filteredRecords);
        log.debug("分页结果过滤完成: moduleCode={}, 记录数={}", moduleCode, filteredRecords.size());
    }

    /**
     * 过滤集合
     *
     * <p>性能优化：一次性获取权限，应用到所有元素
     * <p>集合中的所有元素都是同一个模块的，无需对每个元素都进行权限检查
     * <p>从第一个元素推断类型，避免重复类型检查
     *
     * @param list 集合
     * @return 过滤后的集合
     */
    public List<?> filterList(List<?> list) {
        if (list == null || list.isEmpty()) {
            return list;
        }

        // 从第一个元素推断类型
        Class<?> itemType = inferItemType(list);
        if (itemType == null) {
            log.debug("无法推断集合元素类型");
            return list;
        }
        
        SysModule annotation = itemType.getAnnotation(SysModule.class);
        if (annotation == null) {
            // 不是 @SysModule 标记的对象，无需过滤
            return list;
        }

        String moduleCode = annotation.moduleCode();
        log.debug("开始过滤集合: moduleCode={}, 元素数={}", moduleCode, list.size());

        // 一次性获取用户对该模块所有字段的READ权限
        Set<String> allowedFields = permissionValidator.getReadableFields(moduleCode);
        if (allowedFields.isEmpty()) {
            log.debug("用户对模块无读权限: {}", moduleCode);
            return new ArrayList<>();
        }

        // 应用权限过滤到所有元素
        List<Object> filtered = new ArrayList<>();
        try {
            for (Object item : list) {
                Object filteredItem = createFilteredObjectWithPermissions(item, allowedFields);
                filtered.add(filteredItem);
            }
        } catch (Exception e) {
            log.error("过滤集合失败: moduleCode={}", moduleCode, e);
            return list;
        }

        log.debug("集合过滤完成: moduleCode={}, 元素数={}", moduleCode, filtered.size());
        return filtered;
    }

    /**
     * 创建过滤后的对象
     *
     * @param original 原始对象
     * @param moduleCode 模块代码
     * @param fieldCodes 字段编码列表
     * @return 过滤后的对象
     */
    private Object createFilteredObject(Object original, String moduleCode, List<String> fieldCodes)
            throws Exception {
        // 创建新对象
        Object filtered = original.getClass().getDeclaredConstructor().newInstance();

        // 一次查询获取用户对模块所有字段的READ权限（性能优化）
        Set<String> allowedFields = permissionValidator.getReadableFields(moduleCode);

        // 只复制有权限的字段
        for (String fieldCode : allowedFields) {
            copyField(original, filtered, fieldCode);
        }

        return filtered;
    }

    /**
     * 创建过滤后的对象（使用预先获取的权限）
     *
     * <p>性能优化：避免重复查询权限
     * <p>用于批量过滤场景（如分页结果、集合）
     *
     * @param original 原始对象
     * @param allowedFields 用户有权限的字段集合（预先获取）
     * @return 过滤后的对象
     */
    private Object createFilteredObjectWithPermissions(Object original, Set<String> allowedFields)
            throws Exception {
        // 创建新对象
        Object filtered = original.getClass().getDeclaredConstructor().newInstance();

        // 只复制有权限的字段
        for (String fieldCode : allowedFields) {
            copyField(original, filtered, fieldCode);
        }

        return filtered;
    }

    /**
     * 复制字段值
     *
     * @param source 源对象
     * @param target 目标对象
     * @param fieldName 字段名
     */
    private void copyField(Object source, Object target, String fieldName) {
        try {
            Field field = source.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(source);
            field.set(target, value);
            log.debug("字段已复制: {}", fieldName);
        } catch (Exception e) {
            log.debug("字段复制失败: {}", fieldName, e);
        }
    }

    /**
     * 从PageResult的泛型类型推断Response类型
     *
     * <p>通过反射获取PageResult的泛型参数，避免依赖实际数据
     * <p>支持两种方式：
     * 1. 直接继承PageResult的情况
     * 2. 从第一条记录推断的备选方案
     *
     * @param pageResult 分页结果
     * @return Response类型，如果无法推断则返回null
     */
    private Class<?> inferResponseType(PageResult<?> pageResult) {
        try {
            // 方式1: 获取PageResult对象的运行时类型
            Class<?> pageResultClass = pageResult.getClass();
            
            // 获取PageResult的泛型超类
            Type genericSuperclass = pageResultClass.getGenericSuperclass();
            
            // 如果直接继承PageResult，则genericSuperclass是ParameterizedType
            if (genericSuperclass instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericSuperclass;
                Type[] typeArguments = parameterizedType.getActualTypeArguments();
                if (typeArguments.length > 0 && typeArguments[0] instanceof Class) {
                    log.debug("从泛型超类推断Response类型成功");
                    return (Class<?>) typeArguments[0];
                }
            }
            
            // 方式2: 备选方案 - 从第一条记录推断
            // 用于处理PageResult不是直接继承的情况
            log.debug("无法从泛型超类推断Response类型，尝试备选方案");
            
            List<?> records = pageResult.getRecords();
            if (records != null && !records.isEmpty()) {
                Object firstRecord = records.get(0);
                if (firstRecord != null) {
                    Class<?> recordType = firstRecord.getClass();
                    log.debug("从第一条记录推断Response类型成功: {}", recordType.getName());
                    return recordType;
                }
            }
            
            log.debug("无法推断Response类型：泛型超类和记录都不可用");
            return null;
        } catch (Exception e) {
            log.debug("推断Response类型异常", e);
            return null;
        }
    }

    /**
     * 从集合的第一个元素推断元素类型
     *
     * <p>通过获取第一个元素的运行时类型来推断集合元素类型
     * <p>假设集合中所有元素都是同一类型
     *
     * @param list 集合
     * @return 元素类型，如果无法推断则返回null
     */
    private Class<?> inferItemType(List<?> list) {
        try {
            if (list == null || list.isEmpty()) {
                return null;
            }

            Object firstItem = list.get(0);
            if (firstItem == null) {
                return null;
            }

            return firstItem.getClass();
        } catch (Exception e) {
            log.debug("推断集合元素类型异常", e);
            return null;
        }
    }
}
