package com.jdec.platform.common_business.biz.change;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jdec.platform.common_business.api.dto.common.RowChangeDTO;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class TableUtils {

    public static final Map<String, Map<String, List<String>>> TABLE_DICT_FIELD_CONFIG =
            Map.ofEntries(
                    Map.entry(
                            "user",
                            Map.ofEntries(
                                    Map.entry("sex_id", List.of("sex_id", "sex_name")),
                                    Map.entry(
                                            "performance_type_id",
                                            List.of(
                                                    "performance_type_id",
                                                    "performance_type_name")),
                                    Map.entry(
                                            "user_status_id",
                                            List.of("user_status_id", "user_status_name")),
                                    Map.entry(
                                            "job_status_id",
                                            List.of("job_status_id", "job_status_name")),
                                    Map.entry(
                                            "job_class_id",
                                            List.of("job_class_id", "job_class_name")),
                                    Map.entry(
                                            "job_level_id",
                                            List.of("job_level_id", "job_level_name")),
                                    Map.entry(
                                            "department_id",
                                            List.of("department_id", "department_name")),
                                    Map.entry("job_id", List.of("job_id", "job_name")),
                                    Map.entry(
                                            "hometown_id", List.of("hometown_id", "hometown_name")),
                                    Map.entry(
                                            "job_grade_id",
                                            List.of("job_grade_id", "job_grade_name")),
                                    Map.entry(
                                            "internal_ability_evaluation_id",
                                            List.of(
                                                    "internal_ability_evaluation_id",
                                                    "internal_ability_evaluation_name")),
                                    Map.entry(
                                            "external_ability_evaluation_id",
                                            List.of(
                                                    "external_ability_evaluation_id",
                                                    "external_ability_evaluation_name")),
                                    Map.entry(
                                            "bank_type_id",
                                            List.of("bank_type_id", "bank_type_name")),
                                    Map.entry(
                                            "highest_education_id",
                                            List.of(
                                                    "highest_education_id",
                                                    "highest_education_name")),
                                    Map.entry(
                                            "day_highest_education_id",
                                            List.of(
                                                    "day_highest_education_id",
                                                    "day_highest_education_name")),
                                    Map.entry(
                                            "contract_type",
                                            List.of("contract_type", "contract_name")),
                                    Map.entry(
                                            "wage_type_id",
                                            List.of("wage_type_id", "wage_type_name")))),
                    Map.entry(
                            "user_base",
                            Map.ofEntries(
                                    Map.entry("sex_id", List.of("sex_id", "sex_name")),
                                    Map.entry(
                                            "job_class_id",
                                            List.of("job_class_id", "job_class_name")),
                                    Map.entry(
                                            "job_level_id",
                                            List.of("job_level_id", "job_level_name")),
                                    Map.entry(
                                            "job_grade_id",
                                            List.of("job_grade_id", "job_grade_name")),
                                    Map.entry(
                                            "hometown_id", List.of("hometown_id", "hometown_name")),
                                    Map.entry(
                                            "nationality_id",
                                            List.of("nationality_id", "nationality_name")),
                                    Map.entry(
                                            "political_appearance_id",
                                            List.of(
                                                    "political_appearance_id",
                                                    "political_appearance_name")),
                                    Map.entry(
                                            "day_highest_education_id",
                                            List.of(
                                                    "day_highest_education_id",
                                                    "day_highest_education_name")),
                                    Map.entry(
                                            "highest_education_id",
                                            List.of(
                                                    "highest_education_id",
                                                    "highest_education_name")),
                                    Map.entry("marital_id", List.of("marital_id", "marital_name")),
                                    Map.entry(
                                            "household_type_id",
                                            List.of("household_type_id", "household_type_name")),
                                    Map.entry(
                                            "internal_ability_evaluation_id",
                                            List.of(
                                                    "internal_ability_evaluation_id",
                                                    "internal_ability_evaluation_name")),
                                    Map.entry(
                                            "external_ability_evaluation_id",
                                            List.of(
                                                    "external_ability_evaluation_id",
                                                    "external_ability_evaluation_name")))),
                    Map.entry(
                            "user_agreement",
                            Map.ofEntries(
                                    Map.entry(
                                            "contract_type_id",
                                            List.of("contract_type_id", "contract_type_name")))),
                    Map.entry(
                            "user_card",
                            Map.ofEntries(
                                    Map.entry(
                                            "card_type_id",
                                            List.of("card_type_id", "card_type_name")),
                                    Map.entry(
                                            "bank_type_id",
                                            List.of("bank_type_id", "bank_type_name")))),
                    Map.entry(
                            "user_certificate_disability",
                            Map.ofEntries(
                                    Map.entry(
                                            "disability_type_id",
                                            List.of("disability_type_id", "disability_type_name")),
                                    Map.entry(
                                            "disability_level_id",
                                            List.of(
                                                    "disability_level_id",
                                                    "disability_level_name")))),
                    Map.entry(
                            "user_certificate_qualification",
                            Map.ofEntries(
                                    Map.entry(
                                            "qualification_type_id",
                                            List.of(
                                                    "qualification_type_id",
                                                    "qualification_type_name")),
                                    Map.entry(
                                            "qualification_class_id",
                                            List.of(
                                                    "qualification_class_id",
                                                    "qualification_class_name")),
                                    Map.entry(
                                            "qualification_name_id",
                                            List.of(
                                                    "qualification_name_id",
                                                    "qualification_name_name")),
                                    Map.entry(
                                            "qualification_level_id",
                                            List.of(
                                                    "qualification_level_id",
                                                    "qualification_level_name")),
                                    Map.entry(
                                            "operation_items_id",
                                            List.of(
                                                    "operation_items_id",
                                                    "operation_items_name")))),
                    Map.entry(
                            "user_contact",
                            Map.ofEntries(
                                    Map.entry(
                                            "contact_type_id",
                                            List.of("contact_type_id", "contact_type_name")))),
                    Map.entry(
                            "user_educate",
                            Map.ofEntries(
                                    Map.entry(
                                            "education_id",
                                            List.of("education_id", "education_name")),
                                    Map.entry("degree_id", List.of("degree_id", "degree_name")),
                                    Map.entry(
                                            "study_type_id",
                                            List.of("study_type_id", "study_type_name")),
                                    Map.entry(
                                            "education_type_id",
                                            List.of("education_type_id", "education_type_name")),
                                    Map.entry(
                                            "education_height_id",
                                            List.of(
                                                    "education_height_id",
                                                    "education_height_name")),
                                    Map.entry(
                                            "education_full_time_height_id",
                                            List.of(
                                                    "education_full_time_height_id",
                                                    "education_full_time_height_name")),
                                    Map.entry(
                                            "major_type_id",
                                            List.of("major_type_id", "major_type_name")),
                                    Map.entry(
                                            "academic_type_id",
                                            List.of("academic_type_id", "academic_type_name")))),
                    Map.entry(
                            "user_external_system",
                            Map.ofEntries(
                                    Map.entry(
                                            "external_system_type_id",
                                            List.of(
                                                    "external_system_type_id",
                                                    "external_system_type_name")),
                                    Map.entry(
                                            "login_type_id",
                                            List.of("login_type_id", "login_type_name")),
                                    Map.entry("role_id", List.of("role_id", "role_name")))),
                    Map.entry(
                            "user_item",
                            Map.ofEntries(
                                    Map.entry("type_id", List.of("type_id", "type_name")),
                                    Map.entry("class_id", List.of("class_id", "class_name")),
                                    Map.entry("role_id", List.of("role_id", "role_name")))),
                    Map.entry(
                            "user_salary",
                            Map.ofEntries(
                                    Map.entry("item_id", List.of("item_id", "item_name")),
                                    Map.entry("class_id", List.of("class_id", "class_name")),
                                    Map.entry(
                                            "secrecy_level_id",
                                            List.of("secrecy_level_id", "secrecy_level_name")))),
                    Map.entry(
                            "user_salary_benefits",
                            Map.ofEntries(
                                    Map.entry(
                                            "wage_type_id",
                                            List.of("wage_type_id", "wage_type_name")),
                                    Map.entry(
                                            "performance_type_id",
                                            List.of(
                                                    "performance_type_id",
                                                    "performance_type_name")),
                                    Map.entry(
                                            "secrecy_level_id",
                                            List.of("secrecy_level_id", "secrecy_level_name")))),
                    Map.entry(
                            "user_seal",
                            Map.ofEntries(
                                    Map.entry("class_id", List.of("class_id", "class_name")),
                                    Map.entry(
                                            "seal_unit_id",
                                            List.of("seal_unit_id", "seal_unit_name")),
                                    Map.entry(
                                            "secrecy_level_id",
                                            List.of("secrecy_level_id", "secrecy_level_name")))),
                    Map.entry(
                            "user_secret",
                            Map.ofEntries(
                                    Map.entry(
                                            "secret_type_id",
                                            List.of("secret_type_id", "secret_type_name")),
                                    Map.entry(
                                            "secret_unit_id",
                                            List.of("secret_unit_id", "secret_unit_name")),
                                    Map.entry(
                                            "secrecy_level_id",
                                            List.of("secrecy_level_id", "secrecy_level_name")))),
                    Map.entry(
                            "user_social_security",
                            Map.ofEntries(
                                    Map.entry("item_id", List.of("item_id", "item_name")),
                                    Map.entry("class_id", List.of("class_id", "class_name")),
                                    Map.entry(
                                            "secrecy_level_id",
                                            List.of("secrecy_level_id", "secrecy_level_name")))));

    public static final Set<String> NONE_RECORD_FIELD =
            Set.of(
                    "created_by",
                    "create_by",
                    "created_name",
                    "created_date",
                    "updated_by",
                    "update_by",
                    "updated_name",
                    "updated_date",
                    "deleted",
                    "create_date",
                    "update_date",
                    "subject_id",
                    "subject_name",
                    "source_id");

    public static final Map<String, List<String>> TABLE_UNIQ_FIELD_CONFIG =
            Map.ofEntries(Map.entry("score", List.of("semester", "course_id")));

    public static final Set<String> getTableDictFieldSet(String tableName) {
        if (!TABLE_DICT_FIELD_CONFIG.containsKey(tableName)) {
            return new HashSet<>();
        }
        return TABLE_DICT_FIELD_CONFIG.get(tableName).values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());
    }

    public static final Map<String, List<String>> getTableDictFieldMap(String tableName) {
        if (!TABLE_DICT_FIELD_CONFIG.containsKey(tableName)) {
            return new HashMap<>();
        }
        return TABLE_DICT_FIELD_CONFIG.get(tableName);
    }

    public static final String getTableNameByClassAnnotation(Class clazz) {
        TableName tableName = (TableName) clazz.getDeclaredAnnotation(TableName.class);
        if (tableName == null) {
            return null;
        }
        return tableName.value();
    }

    public static final Set<String> getTableDictFieldSetByAnnotation(Class clazz) {
        String tableName = getTableNameByClassAnnotation(clazz);
        return getTableDictFieldSet(tableName);
    }

    public static final Map<String, List<String>> getTableDictFieldMapByAnnotation(Class clazz) {
        String tableName = getTableNameByClassAnnotation(clazz);
        return getTableDictFieldMap(tableName);
    }

    public static final <T> RowChangeDTO getInsertOrDeleteFieldChangeList(
            T t,
            Map<String, SysFieldDTO> showName,
            Map<String, List<String>> tableUniqueFieldMap,
            Boolean insert) {
        if (t == null) {
            return null;
        }
        Class c = t.getClass();
        Set<String> dictSet = getTableDictFieldSetByAnnotation(c);
        Map<String, List<String>> dictMap = getTableDictFieldMapByAnnotation(c);
        String tableName = getTableNameByClassAnnotation(c);
        Map<String, Field> fieldMap = new HashMap<>();
        List<String> changeField = new ArrayList<>();
        Field[] fs = c.getDeclaredFields();

        List<com.jdec.platform.common_business.api.dto.common.FieldChangeDTO> fieldChangeDTOS =
                new ArrayList<>();
        for (Field f : fs) {
            f.setAccessible(true);
            String fieldName = null;
            TableId tableId = f.getDeclaredAnnotation(TableId.class);
            if (tableId != null && !NONE_RECORD_FIELD.contains(tableId.value())) {
                fieldMap.put(tableId.value(), f);
                fieldName = tableId.value();
            }
            TableField tableField = f.getDeclaredAnnotation(TableField.class);
            if (tableField != null && !NONE_RECORD_FIELD.contains(tableField.value())) {
                fieldMap.put(tableField.value(), f);
                fieldName = tableField.value();
            }
            if (fieldName != null
                    && (!dictSet.contains(fieldName)
                            || (dictSet.contains(fieldName) && dictMap.containsKey(fieldName)))) {
                changeField.add(fieldName);
            }
        }

        for (String fieldName : changeField) {
            String fb = null;
            // 字典
            if (dictMap.containsKey(fieldName)) {
                String objectValue1 = null;
                String objectValue2 = null;
                try {

                    if (fieldMap.get(dictMap.get(fieldName).get(1)).get(t) == null) {
                        continue;
                    }
                    objectValue1 =
                            getStringValueFromObject(
                                    fieldMap.get(dictMap.get(fieldName).get(0)).get(t));
                    objectValue2 =
                            getStringValueFromObject(
                                    fieldMap.get(dictMap.get(fieldName).get(1)).get(t));
                } catch (Exception e) {

                }
                //                if(objectValue1==null){
                //                    continue;
                //                }
                // fb.dataId(objectValue1).dataName(objectValue2);
                fb = objectValue2;
            } else {
                // 非字典
                String objectValue1 = null;
                try {
                    if (fieldMap.get(fieldName).get(t) == null) {
                        continue;
                    }
                    objectValue1 = getStringValueFromObject(fieldMap.get(fieldName).get(t));
                } catch (Exception e) {

                }
                fb = objectValue1;
            }
            com.jdec.platform.common_business.api.dto.common.FieldChangeDTO dto =
                    new com.jdec.platform.common_business.api.dto.common.FieldChangeDTO();
            dto.setField(tableName + "." + fieldName);
            if (showName != null && showName.containsKey(tableName + "." + fieldName)) {
                dto.setName(showName.get(tableName + "." + fieldName).getFieldShowName());
                // dto.setFieldType(showName.get(fieldName).getFieldType());
            }
            if (insert) {
                dto.setNewer(fb);
            } else {
                dto.setOld(fb);
            }
            fieldChangeDTOS.add(dto);
        }
        String uniqName = getUniqName(tableName, tableUniqueFieldMap, fieldMap, t);
        return RowChangeDTO.builder().columns(fieldChangeDTOS).name(uniqName).build();
    }

    public static <T> String getUniqName(
            String tableName,
            Map<String, List<String>> tableUniqueFieldMap,
            Map<String, Field> fieldMap,
            T t) {
        if (tableUniqueFieldMap.containsKey(tableName)) {
            List<String> fs = tableUniqueFieldMap.get(tableName);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < fs.size(); i++) {
                try {
                    String objectValue = getStringValueFromObject(fieldMap.get(fs.get(i)).get(t));
                    sb.append(objectValue);
                } catch (Exception e) {

                }
                if (i != fs.size() - 1) {
                    sb.append(" ");
                }
            }
            return sb.toString();
        }
        return null;
    }

    public static final <T> RowChangeDTO getInsertOrDeleteRowChange(
            T t,
            Map<String, SysFieldDTO> showName,
            Map<String, List<String>> tableUniqueFieldMap,
            Boolean insert) {
        RowChangeDTO rowChangeDTO =
                getInsertOrDeleteFieldChangeList(t, showName, tableUniqueFieldMap, insert);
        return rowChangeDTO;
    }

    public static final <T> List<RowChangeDTO> getInsertOrDeleteRowChangeList(
            List<T> tList,
            Map<String, SysFieldDTO> showName,
            Map<String, List<String>> tableUniqueFieldMap,
            Boolean insert) {
        if (CollectionUtil.isEmpty(tList)) {
            return null;
        }
        List<RowChangeDTO> rows = new ArrayList<>();
        for (T t : tList) {
            RowChangeDTO rowChangeDTO =
                    getInsertOrDeleteRowChange(t, showName, tableUniqueFieldMap, insert);
            rows.add(rowChangeDTO);
        }
        return rows;
    }

    public static final <T> RowChangeDTO getUpdateFieldChangeList(
            T t1,
            T t2,
            Map<String, SysFieldDTO> showName,
            Map<String, List<String>> tableUniqueFieldMap) {
        if (t1 == null) {
            return null;
        }
        Class c = t1.getClass();
        Set<String> dictSet = getTableDictFieldSetByAnnotation(c);
        Map<String, List<String>> dictMap = getTableDictFieldMapByAnnotation(c);
        String tableName = getTableNameByClassAnnotation(c);
        Map<String, Field> fieldMap = new HashMap<>();
        List<String> changeField = new ArrayList<>();
        Field[] fs = c.getDeclaredFields();

        String IdField = null;
        List<com.jdec.platform.common_business.api.dto.common.FieldChangeDTO> fieldChangeDTOS =
                new ArrayList<>();
        for (Field f : fs) {
            f.setAccessible(true);
            String fieldName = null;
            TableId tableId = f.getDeclaredAnnotation(TableId.class);
            if (tableId != null && !NONE_RECORD_FIELD.contains(tableId.value())) {
                fieldMap.put(tableId.value(), f);
                fieldName = tableId.value();
                IdField = tableId.value();
            }
            TableField tableField = f.getDeclaredAnnotation(TableField.class);
            if (tableField != null && !NONE_RECORD_FIELD.contains(tableField.value())) {
                fieldMap.put(tableField.value(), f);
                fieldName = tableField.value();
            }
            if (fieldName != null
                    && (!dictSet.contains(fieldName) || dictMap.containsKey(fieldName))) {
                changeField.add(fieldName);
            }
        }

        for (String fieldName : changeField) {
            // 字典值
            if (dictMap.containsKey(fieldName)) {
                String objectValueBefore1 = null;
                String objectValueBefore2 = null;
                String objectValueAfter1 = null;
                String objectValueAfter2 = null;
                try {

                    if (fieldMap.get(fieldName).get(t1) == null) {
                        continue;
                    }

                    objectValueBefore1 =
                            getStringValueFromObject(
                                    fieldMap.get(dictMap.get(fieldName).get(0)).get(t1));
                    objectValueBefore2 =
                            getStringValueFromObject(
                                    fieldMap.get(dictMap.get(fieldName).get(1)).get(t1));

                    objectValueAfter1 =
                            getStringValueFromObject(
                                    fieldMap.get(dictMap.get(fieldName).get(0)).get(t2));
                    objectValueAfter2 =
                            getStringValueFromObject(
                                    fieldMap.get(dictMap.get(fieldName).get(1)).get(t2));
                } catch (Exception e) {

                }
                if (!isChanged(objectValueBefore2, objectValueAfter2)) {
                    continue;
                }

                com.jdec.platform.common_business.api.dto.common.FieldChangeDTO dto =
                        new com.jdec.platform.common_business.api.dto.common.FieldChangeDTO();
                dto.setField(tableName + "." + fieldName);
                if (showName != null && showName.containsKey(tableName + "." + fieldName)) {
                    dto.setName(showName.get(tableName + "." + fieldName).getFieldShowName());
                    // dto.setFieldType(showName.get(fieldName).getFieldType());
                }

                dto.setOld(objectValueBefore2);
                dto.setNewer(objectValueAfter2);
                fieldChangeDTOS.add(dto);
            } else {
                // 非字典值
                String objectValueBefore = null;
                String objectValueAfter = null;
                try {

                    if (fieldMap.get(fieldName).get(t1) == null) {
                        continue;
                    }

                    objectValueBefore = getStringValueFromObject(fieldMap.get(fieldName).get(t1));
                    objectValueAfter = getStringValueFromObject(fieldMap.get(fieldName).get(t2));
                } catch (Exception e) {

                }
                if (!isChanged(objectValueBefore, objectValueAfter) || fieldName.equals(IdField)) {
                    continue;
                }
                com.jdec.platform.common_business.api.dto.common.FieldChangeDTO dto =
                        new com.jdec.platform.common_business.api.dto.common.FieldChangeDTO();
                dto.setField(tableName + "." + fieldName);

                if (showName != null && showName.containsKey(tableName + "." + fieldName)) {
                    dto.setName(showName.get(tableName + "." + fieldName).getFieldShowName());
                    // dto.setFieldType(showName.get(fieldName).getFieldType());
                }
                dto.setOld(objectValueBefore);
                dto.setNewer(objectValueAfter);
                fieldChangeDTOS.add(dto);
            }
        }
        String uniqName = getUniqName(tableName, tableUniqueFieldMap, fieldMap, t2);
        return RowChangeDTO.builder().columns(fieldChangeDTOS).name(uniqName).build();
    }

    public static final <T> RowChangeDTO getUpdateFieldChangeRow(
            T t1,
            T t2,
            Map<String, SysFieldDTO> showName,
            Map<String, List<String>> tableUniqueFieldMap) {
        RowChangeDTO rowChangeDTO = getUpdateFieldChangeList(t1, t2, showName, tableUniqueFieldMap);
        if (rowChangeDTO == null || CollectionUtil.isEmpty(rowChangeDTO.getColumns())) {
            return null;
        }
        return rowChangeDTO;
    }

    public static final List<RowChangeDTO> getUpdateFieldChangeRowList(
            Map<?, ?> ttMap,
            Map<String, SysFieldDTO> showName,
            Map<String, List<String>> tableUniqueFieldMap) {
        if (CollectionUtil.isEmpty(ttMap)) {
            return null;
        }
        List<RowChangeDTO> rows = new ArrayList<>();
        for (Map.Entry<?, ?> e : ttMap.entrySet()) {
            RowChangeDTO row =
                    getUpdateFieldChangeRow(
                            e.getKey(), e.getValue(), showName, tableUniqueFieldMap);
            if (row != null && CollectionUtil.isNotEmpty(row.getColumns())) {
                rows.add(row);
            }
        }
        return rows;
    }

    public static final String getStringValueFromObject(Object object) {
        if (object == null) {
            return null;
        }
        if (object.getClass().isAssignableFrom(Number.class)
                || object.getClass().isAssignableFrom(String.class)) {
            return String.valueOf(object);
        } else if (object.getClass().isAssignableFrom(LocalDateTime.class)) {

            return DateUtil.format((LocalDateTime) object, DatePattern.NORM_DATE_PATTERN);
        } else {
            return object.toString();
        }
    }

    public static final Boolean isChanged(String valueBefore, String valueAfter) {
        if (valueBefore == null && valueAfter != null) {
            return Boolean.TRUE;
        } else if (valueBefore != null && valueAfter == null) {
            return Boolean.TRUE;
        } else if (valueBefore == null && valueAfter == null) {
            return Boolean.FALSE;
        } else {
            return !valueBefore.equals(valueAfter);
        }
    }
}
