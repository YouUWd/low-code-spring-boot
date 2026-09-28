package com.jdec.platform.dataengine.biz.vo;

import com.jdec.platform.dataengine.biz.dto.FormButton;
import com.jdec.platform.dataengine.biz.dto.InterOperateRight;
import com.jdec.platform.dataengine.biz.dto.TableHeaderConf;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/** 列表接口前端显示VO类 */
@Data
@Builder
public class DynamicListQueryVO {
    @Schema(description = "表头渲染信息")
    private List<TableHeaderConf> columnData;

    @Schema(description = "分页总条数")
    private Integer total;

    /**
     * 业务字段以tableName.tableField格式返回数据, 特殊字段构建时手动填充如 btnList 按钮集合list
     * approvalStatus(暂叫此名，后期可能得改，不带审批链也有状态)来源于业务表business_status字段，带CSS样式(样式后台拼接好还是只返回status指前端统一去拿样式？)
     * createUser 来源于createBy，额外返回用户头像等信息， businessNo 业务编号 业务编号和当前版本(第一版无当前版本)
     */
    @Schema(description = "列表行数据")
    private List<Map<String, Object>> records;

    @Schema(description = "列表行按钮以及创建申请按钮集合")
    private List<FormButton> btnList;

    @Schema(description = "互动权限集合")
    private List<InterOperateRight> interOperateNode;

    // 带审批链需要填充
    @Schema(description = "待办数目")
    private Integer todoNum;

    @Schema(description = "已办数目")
    private Integer doneNum;

    @Schema(description = "全部数目")
    private Integer allNum;
}
