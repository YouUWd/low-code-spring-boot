package com.jdec.platform.dataengine.api.common;

import java.util.Map;
import java.util.Set;

public interface SomeCustomizedFieldsConfig {

    enum CustomizedType {
        // 不做权限验证的字段集合
        UN_PERMISSION_FIELDS_SET,
        // 不做对比的字段集合
        UN_COMPARISON_FIELDS_SET,
        // 不做更新的字段集合
        UN_UPDATE_FIELDS_SET,
    }

    Set<String> PUBLIC_FIELDS_SET =
            Set.of(
                    "id",
                    "subject_id",
                    "business_no",
                    "version_no",
                    "created_by",
                    "updated_by",
                    "created_date",
                    "updated_date",
                    "deleted");
    // "business_status", "active","source_id" ,"node_id",  "parent_node_id"

    public static final Map<String, Map<String, Map<CustomizedType, Set<String>>>>
            CUSTOMIZED_FIELDS_MAP =
                    Map.ofEntries(
                            Map.entry(
                                    "company",
                                    Map.ofEntries(
                                            Map.entry(
                                                    "info",
                                                    Map.ofEntries(
                                                            Map.entry(
                                                                    CustomizedType
                                                                            .UN_PERMISSION_FIELDS_SET,
                                                                    Set.of(
                                                                            "business_status",
                                                                            "active",
                                                                            "source_id")),
                                                            Map.entry(
                                                                    CustomizedType
                                                                            .UN_COMPARISON_FIELDS_SET,
                                                                    Set.of(
                                                                            "business_status",
                                                                            "active",
                                                                            "source_id")),
                                                            Map.entry(
                                                                    CustomizedType
                                                                            .UN_UPDATE_FIELDS_SET,
                                                                    Set.of(
                                                                            "business_status",
                                                                            "active",
                                                                            "source_id")))),
                                            Map.entry(
                                                    "member",
                                                    Map.ofEntries(
                                                            Map.entry(
                                                                    CustomizedType
                                                                            .UN_PERMISSION_FIELDS_SET,
                                                                    Set.of(
                                                                            "node_id",
                                                                            "parent_node_id")),
                                                            Map.entry(
                                                                    CustomizedType
                                                                            .UN_COMPARISON_FIELDS_SET,
                                                                    Set.of(
                                                                            "node_id",
                                                                            "parent_node_id")),
                                                            Map.entry(
                                                                    CustomizedType
                                                                            .UN_UPDATE_FIELDS_SET,
                                                                    Set.of(
                                                                            "node_id",
                                                                            "parent_node_id")))))));

    static Set<String> getCustomizedFieldConfigSet(
            String projectNo, String table, CustomizedType type) {
        Set<String> set =
                CUSTOMIZED_FIELDS_MAP
                        .getOrDefault(projectNo, Map.of())
                        .getOrDefault(table, Map.of())
                        .getOrDefault(type, Set.of());
        set.addAll(PUBLIC_FIELDS_SET);
        return set;
    }
}
