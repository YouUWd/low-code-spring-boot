package com.jdec.platform.shared.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 平台通用跨单据 / 跨实体版本引用协议对象 (Entity Reference)
 *
 * <p>用于在跨单据、跨模块关联以及第三方系统对接时，以自包含、防篡改、多版本精确溯源的形式表达实体引用。
 *
 * <pre>
 * JSON 标准契约示例：
 * {
 *   "business_no": "CR2026102015072600001",  // 全局业务编号（事实来源，必填）
 *   "version_no": 3,                         // 快照版本号（正整数=锁定版本，null/0=动态跟随最新版）
 *   "label": "审批测试01",                    // 冗余展示名称（UI直接回显，必填）
 *   "module_code": "COMPANY-ROOT",           // 目标模块代码（跨模块/第三方调用路由）
 *   "id": 100,                               // 物理主键ID（仅作只读点查缓存提示）
 *   "extra": {}                              // 扩展键值对（可选回显信息）
 * }
 * </pre>
 *
 * @author JDEC Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "平台跨单据/跨实体版本引用标准对象")
public class EntityRef implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 全局唯一业务编号（仲裁唯一事实来源） */
    @JsonProperty("business_no")
    @JsonAlias({"value", "businessNo", "business_no"})
    @Schema(
            description = "全局唯一业务编号（仲裁唯一事实来源）",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "CR2026102015072600001")
    private String businessNo;

    /** 快照版本号（正整数=锁定指定历史版本；null 或 <=0 表示动态追踪最新生效版本） */
    @JsonProperty("version_no")
    @JsonAlias({"versionNo", "version_no"})
    @Schema(description = "快照版本号（正整数=锁定指定版本；null=动态追踪最新生效版）", example = "3")
    private Integer versionNo;

    /** 主要展示名称（供前端组件与单据直接回显） */
    @JsonProperty("label")
    @JsonAlias({"name", "label", "title"})
    @Schema(
            description = "主要展示名称（UI回显）",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "审批测试01")
    private String label;

    /** 目标所属模块代码（跨模块/第三方调用时用于反查元数据与物理表） */
    @JsonProperty("module_code")
    @JsonAlias({"moduleCode", "module_code"})
    @Schema(description = "目标所属模块代码", example = "COMPANY-ROOT")
    private String moduleCode;

    /** 物理主键 ID（仅作为只读性能缓存提示，冲突时以 business_no 为准） */
    @JsonProperty("id")
    @JsonAlias({"id"})
    @Schema(description = "物理主键ID（只读缓存提示）", example = "100")
    private Long id;

    /** 附加扩展数据（用于传递税号、编码、组织等扩展信息） */
    @JsonProperty("extra")
    @JsonAlias({"extra", "extra_data", "extraData"})
    @Schema(description = "附加扩展数据键值对")
    private Map<String, Object> extra;

    /** 是否为严格版本锁定（version_no 为正整数） */
    public boolean isVersionLocked() {
        return versionNo != null && versionNo > 0;
    }

    /**
     * 生成标准资源定位 URI
     *
     * <p>格式：{@code jdec://{moduleCode}/{businessNo}@{versionNo}}
     */
    public String toUri() {
        String module = (moduleCode != null && !moduleCode.isBlank()) ? moduleCode : "unknown";
        String ver = isVersionLocked() ? String.valueOf(versionNo) : "latest";
        String bizNo = (businessNo != null) ? businessNo : "";
        return "jdec://" + module + "/" + bizNo + "@" + ver;
    }

    /** 安全获取 extra 属性，避免 NullPointerException */
    public Map<String, Object> safeExtra() {
        return extra != null ? extra : Collections.emptyMap();
    }

    /**
     * 构建静态锁版引用对象
     *
     * @param businessNo 业务单号
     * @param versionNo 版本号
     * @param label 展示名称
     * @return EntityRef 实例
     */
    public static EntityRef of(String businessNo, Integer versionNo, String label) {
        return EntityRef.builder().businessNo(businessNo).versionNo(versionNo).label(label).build();
    }

    /**
     * 构建包含模块信息的静态锁版引用对象
     *
     * @param moduleCode 模块标识
     * @param businessNo 业务单号
     * @param versionNo 版本号
     * @param label 展示名称
     * @return EntityRef 实例
     */
    public static EntityRef of(
            String moduleCode, String businessNo, Integer versionNo, String label) {
        return EntityRef.builder()
                .moduleCode(moduleCode)
                .businessNo(businessNo)
                .versionNo(versionNo)
                .label(label)
                .build();
    }

    /**
     * 从 JSON 文本或对象安全解析出 EntityRef，格式不符或为空时返回 null
     *
     * @param jsonTextOrObject JSON 字符串、JsonNode 或 Map
     * @return 解析出的 EntityRef，或 null
     */
    public static EntityRef from(Object jsonTextOrObject) {
        if (jsonTextOrObject == null) {
            return null;
        }
        if (jsonTextOrObject instanceof EntityRef ref) {
            return ref;
        }
        try {
            if (jsonTextOrObject instanceof String str) {
                String trimmed = str.trim();
                if (trimmed.isEmpty()
                        || "null".equalsIgnoreCase(trimmed)
                        || "{}".equals(trimmed)
                        || "[]".equals(trimmed)) {
                    return null;
                }
                return OBJECT_MAPPER.readValue(trimmed, EntityRef.class);
            }
            if (jsonTextOrObject instanceof JsonNode node) {
                if (node.isNull() || node.isMissingNode() || !node.isObject()) {
                    return null;
                }
                return OBJECT_MAPPER.treeToValue(node, EntityRef.class);
            }
            if (jsonTextOrObject instanceof Map<?, ?> map) {
                return OBJECT_MAPPER.convertValue(map, EntityRef.class);
            }
        } catch (Exception ignored) {
            // 解析失败降级为 null
        }
        return null;
    }

    /** 转换为标准的 JSON 序列化字符串 */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }
}
