package com.jdec.platform.common.biz.postprocess.enhance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jdec.platform.common.api.dto.response.StatusSimpleResp;
import com.jdec.platform.common.api.dto.response.UserSimpleResp;
import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.dto.request.QueryUsersByIdsReq;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.config.api.dto.response.ModuleApprovalChainConfigResp;
import com.jdec.platform.config.api.dto.response.SysUserPageResp;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryResultEnhanceServiceTest {

    private SysUserApi sysUserApi;
    private SysStatusApi sysStatusApi;
    private QueryResultEnhanceServiceImpl enhanceService;

    @BeforeEach
    void setUp() {
        sysUserApi = mock(SysUserApi.class);
        sysStatusApi = mock(SysStatusApi.class);
        enhanceService = new QueryResultEnhanceServiceImpl(sysUserApi, sysStatusApi);
    }

    @Test
    @DisplayName("enhanceDataRows: 仅处理第一层数据行增强，不递归处理子区块")
    void testEnhanceDataRows_OnlyFirstLayer() {
        // 准备 Mock
        SysUserPageResp userResp = new SysUserPageResp();
        userResp.setUserId(1001L);
        userResp.setUserName("张三");
        userResp.setWorkNumber("W001");
        userResp.setUserAvatar("avatar.png");
        when(sysUserApi.getUsersByUserIds(any(QueryUsersByIdsReq.class)))
                .thenReturn(List.of(userResp));

        ModuleApprovalChainConfigResp.StatusNode statusNode =
                new ModuleApprovalChainConfigResp.StatusNode();
        statusNode.setStatusValue(1);
        statusNode.setTitle("审批中");
        statusNode.setStatusBackground("#fff");
        statusNode.setStatusFontColor("#000");
        when(sysStatusApi.getStatusByValue(1, 100L)).thenReturn(statusNode);

        // 构造数据行（包含根层与子区块）
        DataRow parentRow = new DataRow();
        parentRow.putValue("company", "created_by", 1001L);
        parentRow.putValue("company", "business_status", 1);

        DataRow childRow = new DataRow();
        childRow.putValue("company", "created_by", 1001L);
        childRow.putValue("company", "business_status", 1);

        DataBlock childBlock = new DataBlock();
        childBlock.setRecords(List.of(childRow));
        parentRow.setChildren(List.of(childBlock));

        enhanceService.enhanceDataRows(List.of(parentRow), 100L, "company");

        // 验证第一层主表增强
        Object parentUser = parentRow.getValue("company", "created_by");
        assertInstanceOf(UserSimpleResp.class, parentUser);
        assertEquals("张三", ((UserSimpleResp) parentUser).getUserName());

        Object parentStatus = parentRow.getValue("company", "business_status");
        assertInstanceOf(StatusSimpleResp.class, parentStatus);
        assertEquals("审批中", ((StatusSimpleResp) parentStatus).getTitle());

        // 验证子区块不被递归处理，保持原值
        Object childUser = childRow.getValue("company", "created_by");
        assertEquals(1001L, childUser);
    }

    @Test
    @DisplayName("enhanceOptions: 用户类型候选项转换")
    void testEnhanceOptions_User() {
        SysUserPageResp user = new SysUserPageResp();
        user.setUserId(2001L);
        user.setUserName("李四");
        when(sysUserApi.getUsersByUserIds(any())).thenReturn(List.of(user));

        List<DataOptionResp> raw =
                List.of(
                        DataOptionResp.builder().label(2001L).value(2001L).build(),
                        DataOptionResp.builder().label(2002L).value(2002L).build());

        List<DataOptionResp> enhanced = enhanceService.enhanceOptions(raw, "info.created_by", 100L);

        assertEquals(2, enhanced.size());
        assertInstanceOf(UserSimpleResp.class, enhanced.get(0).getValue());
        assertEquals("李四", ((UserSimpleResp) enhanced.get(0).getValue()).getUserName());
        // 2002 兜底
        assertInstanceOf(UserSimpleResp.class, enhanced.get(1).getValue());
        assertEquals(2002L, ((UserSimpleResp) enhanced.get(1).getValue()).getUserId());
    }

    @Test
    @DisplayName("enhanceOptions: 业务状态类型候选项转换")
    void testEnhanceOptions_Status() {
        ModuleApprovalChainConfigResp.StatusNode statusNode =
                new ModuleApprovalChainConfigResp.StatusNode();
        statusNode.setStatusValue(2);
        statusNode.setTitle("已通过");
        when(sysStatusApi.getStatusByValue(2, 100L)).thenReturn(statusNode);

        List<DataOptionResp> raw = List.of(DataOptionResp.builder().label(2).value(2).build());

        List<DataOptionResp> enhanced =
                enhanceService.enhanceOptions(raw, "business_status", 100L, "status");

        assertEquals(1, enhanced.size());
        assertInstanceOf(StatusSimpleResp.class, enhanced.get(0).getValue());
        assertEquals("已通过", ((StatusSimpleResp) enhanced.get(0).getValue()).getTitle());
    }

    @Test
    @DisplayName("enhanceOptions: 行业类型树状过滤与兜底")
    void testEnhanceOptions_Industry() {
        List<DataOptionResp> raw =
                List.of(DataOptionResp.builder().label("A01").value("A01").build());

        List<DataOptionResp> enhanced = enhanceService.enhanceOptions(raw, "industry_types", 100L);

        assertNotNull(enhanced);
    }

    @Test
    @DisplayName("enhanceOptions: 词云候选项去重与字段剥离")
    void testEnhanceOptions_WordCloud() {
        String json1 = "{\"id\":10, \"name\":\"AI\", \"relation_no\":\"R1\"}";
        String json2 = "{\"relation_no\":\"R2\", \"name\":\"AI\", \"id\":20}"; // 语义相同但字段不同/顺序不同

        List<DataOptionResp> raw =
                List.of(
                        DataOptionResp.builder().label(json1).value(json1).build(),
                        DataOptionResp.builder().label(json2).value(json2).build());

        List<DataOptionResp> enhanced =
                enhanceService.enhanceOptions(raw, "keywords", 100L, "word_cloud");

        // 语义去重后应只有 1 个
        assertEquals(1, enhanced.size());
        Map<?, ?> valMap = (Map<?, ?>) enhanced.get(0).getValue();
        assertEquals("AI", valMap.get("name"));
        assertNull(valMap.get("id"));
        assertNull(valMap.get("relation_no"));
    }

    @Test
    @DisplayName("enhanceOptions: 表头存在 relationWord 时自动触发词云增强")
    void testEnhanceOptions_RelationWordHeader() {
        MenuHeaderColumnResp headerNode =
                MenuHeaderColumnResp.builder()
                        .fieldId(888L)
                        .dataIndex("tag")
                        .relationWord("company_tag")
                        .build();

        String json = "{\"id\":1, \"title\":\"TagA\"}";
        List<DataOptionResp> raw =
                List.of(DataOptionResp.builder().label(json).value(json).build());

        List<DataOptionResp> enhanced =
                enhanceService.enhanceOptions(raw, "tag", 100L, headerNode, 888L);

        assertEquals(1, enhanced.size());
        Map<?, ?> valMap = (Map<?, ?>) enhanced.get(0).getValue();
        assertEquals("TagA", valMap.get("title"));
        assertNull(valMap.get("id"));
    }
}
