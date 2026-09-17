package com.jdec.platform.data.api.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 保存用户个性化表头配置请求（树形结构）
 *
 * <p>节点结构与 {@code SaveMenuReq.MenuHeaderNode} 一致，额外携带用户id：{@code id} 有值的节点按主键更新、无值的新增。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "保存用户个性化表头配置请求")
public class SaveUserMenuHeaderReq implements Serializable {

    @Schema(description = "用户id")
    private Long userId;

    @Schema(description = "主键id（用户表中已存在的节点带值，新增节点传空）")
    private Long id;

    @Schema(description = "虚拟id(类型-id)，如 MENU_47 / Field_10")
    private String virId;

    @Schema(description = "父节点虚拟id(顶层为 0)")
    private String parentId;

    @Schema(description = "所属菜单ID（传列表菜单ID，落库时以其详情菜单ID 为准）", example = "1")
    private Long menuId;

    @Schema(description = "显示名（覆盖值或默认取菜单名/字段显示名）", example = "外部对象列表")
    private String label;

    @Schema(description = "节点类型：0-菜单，1-字段", example = "0")
    private Integer nodeType;

    @Schema(description = "来源ID（nodeType=0 时=来源菜单ID，nodeType=1 时=来源字段ID）", example = "1")
    private Long sourceId;

    @Schema(description = "模块id（nodeType=0 时=菜单绑定模块id，nodeType=1 时=字段所属模块id）", example = "1001")
    private Long moduleId;

    @Schema(description = "字段索引（模块id.表名.字段名）", example = "1001.info.company_name")
    private String dataIndex;

    @Schema(description = "菜单显示颜色（落库时序列化为 JSON 串）")
    private List<String> colors;

    @Schema(description = "列宽(px)")
    private Integer width;

    @Schema(description = "可排序：0-否，1-是", example = "1")
    private Integer sorted;

    @Schema(description = "搜索控件类型")
    private String searchType;

    @Schema(description = "文本是否省略：0-否，1-是", example = "0")
    private Integer ellipsis;

    @Schema(description = "固定：left-左固定，right-右固定，空-不固定")
    private String fixed;

    @Schema(description = "是否选中：0-否，1-是", example = "0")
    private Integer checked;

    @Schema(description = "排序值")
    private Integer sortValue;

    @Schema(description = "子节点列表")
    private List<SaveUserMenuHeaderReq> children;
}
