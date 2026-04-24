package com.jdec.platform.shared.permission;

import com.jdec.platform.shared.annotation.ApiModule;
import com.jdec.platform.shared.model.PageResult;
import com.jdec.platform.shared.service.ModuleFieldQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 字段过滤引擎
 *
 * <p>根据用户权限过滤 @ApiModule 标记的对象的字段
 * <p>支持单个对象、集合、分页结果的过滤
 * <p>支持嵌套对象的递归过滤（带深度限制和循环引用检测）
 * <p>字段信息从权限系统查询，支持人工更新
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FieldFilterEngine {

    private final PermissionValidator permissionValidator;
    private final ModuleFieldQueryService moduleFieldQueryService;

    // 递归过滤的最大深度，防止无限递归
    private static final int MAX_FILTER_DEPTH = 3;
    
    // 线程本地变量用于存储当前过滤的对象引用，用于检测循环引用
    private static final ThreadLocal<java.util.Set<Integer>> PROCESSING_OBJECTS = 
            ThreadLocal.withInitial(java.util.HashSet::new);

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

        try {
            PROCESSING_OBJECTS.set(new java.util.HashSet<>());
            return filterObjectInternal(obj, 0);
        } finally {
            PROCESSING_OBJECTS.remove();
        }
    }

    /**
     * 内部递归过滤方法
     *
     * @param obj 待过滤的对象
     * @param depth 当前递归深度
     * @return 过滤后的对象
     */
    private Object filterObjectInternal(Object obj, int depth) {
        if (obj == null) {
            return null;
        }

        // 检查递归深度
        if (depth > MAX_FILTER_DEPTH) {
            log.debug("达到最大过滤深度 ({}), 停止递归过滤", MAX_FILTER_DEPTH);
            return obj;
        }

        // 检查循环引用
        int objectId = System.identityHashCode(obj);
        Set<Integer> processingObjects = PROCESSING_OBJECTS.get();
        if (processingObjects.contains(objectId)) {
            log.debug("检测到循环引用，停止递归过滤");
            return obj;
        }
        processingObjects.add(objectId);

        try {
            // 检查是否为 @ApiModule 标记的对象
            ApiModule annotation = obj.getClass().getAnnotation(ApiModule.class);
            if (annotation == null) {
                return obj;
            }

            String moduleCode = annotation.moduleCode();
            log.debug("开始过滤 @ApiModule 对象: {} (深度: {})", moduleCode, depth);

            // 从权限系统获取模块的字段编码
            List<String> fieldCodes = moduleFieldQueryService.getFieldCodesByModuleCode(moduleCode);
            if (fieldCodes == null || fieldCodes.isEmpty()) {
                log.debug("模块未注册或无字段: {}", moduleCode);
                return obj;
            }

            // 创建过滤后的对象
            try {
                Object filtered = createFilteredObject(obj, moduleCode, fieldCodes, depth);
                log.debug("@ApiModule 对象过滤完成: {}", moduleCode);
                return filtered;
            } catch (Exception e) {
                log.error("过滤 @ApiModule 对象失败: {}", moduleCode, e);
                return obj;
            }
        } finally {
            processingObjects.remove(objectId);
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

        ApiModule annotation = responseType.getAnnotation(ApiModule.class);
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
        
        ApiModule annotation = itemType.getAnnotation(ApiModule.class);
        if (annotation == null) {
            // 不是 @ApiModule 标记的对象，无需过滤
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
     * <p>支持自动检测并过滤未注册的嵌套对象字段
     *
     * @param original 原始对象
     * @param moduleCode 模块代码
     * @param fieldCodes 字段编码列表
     * @param depth 当前递归深度
     * @return 过滤后的对象
     */
    private Object createFilteredObject(Object original, String moduleCode, List<String> fieldCodes, int depth)
            throws Exception {
        // 创建新对象
        Object filtered = original.getClass().getDeclaredConstructor().newInstance();

        // 一次查询获取用户对模块所有字段的READ权限（性能优化）
        Set<String> allowedFields = permissionValidator.getReadableFields(moduleCode);

        // 复制所有字段，包括未注册的嵌套对象字段
        copyAllFieldsWithNestedDetection(original, filtered, allowedFields, depth);

        return filtered;
    }

    /**
     * 创建过滤后的对象（使用预先获取的权限）
     *
     * <p>性能优化：避免重复查询权限
     * <p>用于批量过滤场景（如分页结果、集合）
     * <p>支持自动检测并过滤未注册的嵌套对象字段
     *
     * @param original 原始对象
     * @param allowedFields 用户有权限的字段集合（预先获取）
     * @return 过滤后的对象
     */
    private Object createFilteredObjectWithPermissions(Object original, Set<String> allowedFields)
            throws Exception {
        // 创建新对象
        Object filtered = original.getClass().getDeclaredConstructor().newInstance();

        // 复制所有字段，包括未注册的嵌套对象字段
        copyAllFieldsWithNestedDetection(original, filtered, allowedFields, 0);

        return filtered;
    }

    /**
     * 复制字段值，支持嵌套对象的递归过滤
     *
     * <p>对于 @ApiModule 标记的嵌套对象，递归进行过滤
     * <p>对于 List 类型的字段，过滤列表中的每个元素
     * <p>对于其他类型的字段，直接复制
     *
     * @param source 源对象
     * @param target 目标对象
     * @param fieldName 字段名
     * @param depth 当前递归深度
     */
    private void copyField(Object source, Object target, String fieldName, int depth) {
        try {
            Field field = getFieldRecursively(source.getClass(), fieldName);
            if (field == null) {
                log.debug("字段不存在: {}", fieldName);
                return;
            }
            field.setAccessible(true);
            Object value = field.get(source);
            
            // 递归过滤嵌套对象
            if (value != null && depth < MAX_FILTER_DEPTH) {
                if (value instanceof List) {
                    // 过滤列表中的每个元素
                    value = filterListInternal((List<?>) value, depth + 1);
                } else if (value.getClass().getAnnotation(ApiModule.class) != null) {
                    // 递归过滤嵌套的 @ApiModule 对象
                    value = filterObjectInternal(value, depth + 1);
                }
            }
            
            field.set(target, value);
            log.debug("字段已复制: {} (深度: {})", fieldName, depth);
        } catch (Exception e) {
            log.debug("字段复制失败: {}", fieldName, e);
        }
    }

    /**
     * 扫描对象的所有字段，包括嵌套对象字段
     *
     * <p>此方法用于获取对象的所有字段，包括：
     * <p>1. 已注册的字段（来自权限系统）
     * <p>2. 未注册但标记了 @ApiModule 的嵌套对象字段
     *
     * @param obj 待扫描的对象
     * @return 所有字段名集合
     */
    private Set<String> getAllFieldsIncludingNested(Object obj) {
        Set<String> allFields = new HashSet<>();
        
        try {
            Class<?> clazz = obj.getClass();
            java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
            
            for (java.lang.reflect.Field field : fields) {
                // 跳过 static 和 final 字段
                int modifiers = field.getModifiers();
                if (java.lang.reflect.Modifier.isStatic(modifiers) || 
                    java.lang.reflect.Modifier.isFinal(modifiers)) {
                    continue;
                }
                
                String fieldName = field.getName();
                allFields.add(fieldName);
            }
        } catch (Exception e) {
            log.debug("扫描对象字段异常", e);
        }
        
        return allFields;
    }

    /**
     * 复制所有字段，包括未注册的嵌套对象字段
     *
     * <p>此方法会：
     * <p>1. 复制所有已注册且用户有权限的字段
     * <p>2. 自动检测并复制未注册但标记了 @ApiModule 的嵌套对象字段
     *
     * @param source 源对象
     * @param target 目标对象
     * @param allowedFields 用户有权限的字段集合
     * @param depth 当前递归深度
     */
    private void copyAllFieldsWithNestedDetection(Object source, Object target, Set<String> allowedFields, int depth) {
        // 第一步：复制所有已注册且用户有权限的字段
        for (String fieldCode : allowedFields) {
            copyField(source, target, fieldCode, depth);
        }
        
        // 第二步：自动检测并复制未注册的嵌套对象字段
        if (depth < MAX_FILTER_DEPTH) {
            Set<String> allFields = getAllFieldsIncludingNested(source);
            Set<String> unregisteredFields = new HashSet<>(allFields);
            unregisteredFields.removeAll(allowedFields);
            
            for (String fieldName : unregisteredFields) {
                try {
                    Field field = getFieldRecursively(source.getClass(), fieldName);
                    if (field == null) {
                        continue;
                    }
                    
                    field.setAccessible(true);
                    Object value = field.get(source);
                    
                    // 检查是否为嵌套对象
                    if (value != null) {
                        if (value instanceof List) {
                            // 检查列表元素是否为 @ApiModule 对象
                            List<?> list = (List<?>) value;
                            if (!list.isEmpty()) {
                                Object firstItem = list.get(0);
                                if (firstItem != null && 
                                    firstItem.getClass().getAnnotation(ApiModule.class) != null) {
                                    // 递归过滤列表
                                    Object filteredList = filterListInternal(list, depth + 1);
                                    Field targetField = getFieldRecursively(target.getClass(), fieldName);
                                    if (targetField != null) {
                                        targetField.setAccessible(true);
                                        targetField.set(target, filteredList);
                                        log.debug("未注册的嵌套列表字段已复制并过滤: {} (深度: {})", fieldName, depth);
                                    }
                                }
                            }
                        } else if (value.getClass().getAnnotation(ApiModule.class) != null) {
                            // 递归过滤嵌套对象
                            Object filteredValue = filterObjectInternal(value, depth + 1);
                            Field targetField = getFieldRecursively(target.getClass(), fieldName);
                            if (targetField != null) {
                                targetField.setAccessible(true);
                                targetField.set(target, filteredValue);
                                log.debug("未注册的嵌套对象字段已复制并过滤: {} (深度: {})", fieldName, depth);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("处理未注册的嵌套字段失败: {}", fieldName, e);
                }
            }
        }
    }

    /**
     * 内部列表过滤方法（用于递归过滤）
     *
     * @param list 待过滤的列表
     * @param depth 当前递归深度
     * @return 过滤后的列表
     */
    private List<?> filterListInternal(List<?> list, int depth) {
        if (list == null || list.isEmpty()) {
            return list;
        }

        // 从第一个元素推断类型
        Class<?> itemType = inferItemType(list);
        if (itemType == null) {
            log.debug("无法推断集合元素类型");
            return list;
        }
        
        ApiModule annotation = itemType.getAnnotation(ApiModule.class);
        if (annotation == null) {
            // 不是 @ApiModule 标记的对象，无需过滤
            return list;
        }

        String moduleCode = annotation.moduleCode();
        log.debug("开始过滤嵌套列表: moduleCode={}, 元素数={}, 深度={}", moduleCode, list.size(), depth);

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
                // 递归过滤嵌套对象
                if (depth < MAX_FILTER_DEPTH) {
                    filteredItem = filterObjectInternal(filteredItem, depth + 1);
                }
                filtered.add(filteredItem);
            }
        } catch (Exception e) {
            log.error("过滤嵌套列表失败: moduleCode={}", moduleCode, e);
            return list;
        }

        log.debug("嵌套列表过滤完成: moduleCode={}, 元素数={}", moduleCode, filtered.size());
        return filtered;
    }

    /**
     * 递归获取字段，支持继承的字段
     *
     * @param clazz 类
     * @param fieldName 字段名
     * @return 字段对象，如果不存在则返回null
     */
    private Field getFieldRecursively(Class<?> clazz, String fieldName) {
        if (clazz == null) {
            return null;
        }

        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            // 在父类中查找
            return getFieldRecursively(clazz.getSuperclass(), fieldName);
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
