package com.jdec.platform.config.biz.reference;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 引用关系注册表。
 *
 * <p>启动时扫描实体包下所有带 {@code @TableName} 的类，解析其字段上的 {@link Reference} 注解， 反转构建索引 {@code 目标表.目标列 ->
 * 引用关系列表}，供 {@link ReferenceCheckService} 查询。
 */
@Slf4j
@Component
public class ReferenceRegistry {

    /** 被扫描的实体包。 */
    private static final String ENTITY_BASE_PACKAGE = "com.jdec.platform.config.biz.entity";

    /** key = targetTable + "." + targetField。 */
    private final Map<String, List<ReferenceMetadata>> index = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));
        for (BeanDefinition definition : scanner.findCandidateComponents(ENTITY_BASE_PACKAGE)) {
            String className = definition.getBeanClassName();
            if (className == null) {
                continue;
            }
            try {
                register(Class.forName(className));
            } catch (ClassNotFoundException e) {
                log.warn("引用注册表扫描实体失败: {}", className, e);
            }
        }
        int total = index.values().stream().mapToInt(List::size).sum();
        log.info("引用关系注册表初始化完成，共注册 {} 条引用关系", total);
    }

    /** 查询某被引用实体的所有引用关系（被引用列固定为主键 id）。 */
    public List<ReferenceMetadata> findByTarget(Class<?> targetEntity) {
        return findByTarget(targetEntity, "id");
    }

    /** 查询某被引用实体指定列的所有引用关系。 */
    public List<ReferenceMetadata> findByTarget(Class<?> targetEntity, String targetField) {
        String targetTable = resolveTableName(targetEntity);
        if (targetTable == null) {
            return List.of();
        }
        return index.getOrDefault(targetTable + "." + targetField, List.of());
    }

    private void register(Class<?> entity) {
        String sourceTable = resolveTableName(entity);
        String sourcePk = resolvePrimaryKeyColumn(entity);
        for (Field field : allFields(entity)) {
            Reference reference = field.getAnnotation(Reference.class);
            if (reference == null || !reference.check()) {
                continue;
            }
            if (isNotPersistColumn(field)) {
                continue;
            }
            String targetTable = resolveTableName(reference.target());
            if (targetTable == null) {
                log.warn(
                        "被引用实体 {} 缺少 @TableName，忽略 {}.{}",
                        reference.target().getName(),
                        entity.getSimpleName(),
                        field.getName());
                continue;
            }
            String sourceField =
                    StringUtils.hasText(reference.sourceField())
                            ? reference.sourceField()
                            : resolveColumn(entity, field.getName());
            String displayField =
                    StringUtils.hasText(reference.displayField())
                            ? resolveColumn(entity, reference.displayField())
                            : null;
            String name = StringUtils.hasText(reference.name()) ? reference.name() : sourceTable;
            ReferenceMetadata metadata =
                    new ReferenceMetadata(
                            entity,
                            sourceTable,
                            sourceField,
                            sourcePk,
                            displayField,
                            name,
                            reference.target(),
                            targetTable,
                            reference.targetField());
            index.computeIfAbsent(
                            targetTable + "." + reference.targetField(), k -> new ArrayList<>())
                    .add(metadata);
        }
    }

    /** 读取实体的表名；无 {@code @TableName} 或为空时返回 null。 */
    private String resolveTableName(Class<?> entity) {
        TableName tableName = entity.getAnnotation(TableName.class);
        if (tableName == null || !StringUtils.hasText(tableName.value())) {
            return null;
        }
        return tableName.value();
    }

    /** 读取实体主键列名，缺省为 id。 */
    private String resolvePrimaryKeyColumn(Class<?> entity) {
        for (Field field : allFields(entity)) {
            if (field.isAnnotationPresent(TableId.class)) {
                return resolveColumn(entity, field.getName());
            }
        }
        return "id";
    }

    /** 解析属性名对应的数据库列名。 */
    private String resolveColumn(Class<?> entity, String property) {
        for (Field field : allFields(entity)) {
            if (field.getName().equals(property)) {
                TableField tableField = field.getAnnotation(TableField.class);
                if (tableField != null && StringUtils.hasText(tableField.value())) {
                    return tableField.value();
                }
                return camelToSnake(property);
            }
        }
        return camelToSnake(property);
    }

    private boolean isNotPersistColumn(Field field) {
        if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
            return true;
        }
        TableField tableField = field.getAnnotation(TableField.class);
        return tableField != null && !tableField.exist();
    }

    private List<Field> allFields(Class<?> entity) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = entity;
                current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
        }
        return fields;
    }

    private String camelToSnake(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
