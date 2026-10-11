package com.jdec.platform.data.biz;

import static org.junit.jupiter.api.Assertions.*;

import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.biz.util.TreeMergeUtils;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 单据版本派生与局部增量融合工具单元测试 */
@DisplayName("单据版本派生与局部增量融合测试 (Fork & Deep Merge)")
public class ForkVersionEngineTest {

    @Test
    @DisplayName("测试用例 1: 完整克隆 (patchData 为空时 100% 继承并擦除旧 ID 与审计字段)")
    void testPureCloneWithoutPatch() {
        DataBlock base156 = buildBaseRecord156();

        DataBlock merged = TreeMergeUtils.deepMerge(base156, null);
        assertNotNull(merged);
        DataRow mainRow = merged.getFirstRecord();
        assertNotNull(mainRow);

        // 验证主表原值继承
        assertEquals(156L, mainRow.getLong("orders", "id"));
        assertEquals("张三", mainRow.getString("orders", "name"));
        assertEquals("SO-2026-001", mainRow.getString("orders", "order_no"));

        // 验证子表原值继承
        DataBlock contactBlock = mainRow.getChild(200L);
        assertNotNull(contactBlock);
        assertEquals(2, contactBlock.size());

        // 执行主键与审计擦除
        TreeMergeUtils.stripIdsAndResetAudit(merged);

        // 验证主表主键和审计列已被彻底擦除
        assertNull(mainRow.getValue("orders", "id"));
        assertNull(mainRow.getValue("orders", "created_at"));
        assertNull(mainRow.getValue("orders", "updated_at"));
        assertEquals("张三", mainRow.getString("orders", "name"));
        assertEquals(0, mainRow.getInteger("orders", "deleted"));

        // 验证子表明细主键和审计列已被擦除
        DataBlock cleanedContactBlock = mainRow.getChild(200L);
        for (DataRow item : cleanedContactBlock.getRecords()) {
            assertNull(item.getValue("order_contact", "id"));
            assertNull(item.getValue("order_contact", "created_at"));
            assertEquals(0, item.getInteger("order_contact", "deleted"));
        }

        // 验证原 156 数据未被破坏 (深拷贝隔离)
        assertEquals(156L, base156.getFirstRecord().getLong("orders", "id"));
        assertNotNull(base156.getFirstRecord().getValue("orders", "created_at"));
    }

    @Test
    @DisplayName("测试用例 2: 主表局部字段修改覆盖，未提交字段及子表完全继承")
    void testPartialMainTableOverride() {
        DataBlock base156 = buildBaseRecord156();

        // patch 只改 name，不传 order_no，不传子表
        DataRow patchRow = new DataRow();
        patchRow.putValue("orders", "name", "李四 (已更新)");
        DataBlock patch = DataBlock.of(100L, patchRow);

        DataBlock merged = TreeMergeUtils.deepMerge(base156, patch);
        TreeMergeUtils.stripIdsAndResetAudit(merged);

        DataRow mainRow = merged.getFirstRecord();
        assertNotNull(mainRow);

        // 断言: name 被覆盖为李四，order_no 依然继承自 156
        assertEquals("李四 (已更新)", mainRow.getString("orders", "name"));
        assertEquals("SO-2026-001", mainRow.getString("orders", "order_no"));
        assertNull(mainRow.getValue("orders", "id"));

        // 断言: 子表完全继承自 156
        DataBlock contactBlock = mainRow.getChild(200L);
        assertNotNull(contactBlock);
        assertEquals(2, contactBlock.size());
        assertEquals(
                "13800000000",
                contactBlock.getRecords().get(0).getString("order_contact", "phone"));
        assertEquals(
                "13900000000",
                contactBlock.getRecords().get(1).getString("order_contact", "phone"));
    }

    @Test
    @DisplayName("测试用例 3: 子表明细增、删、改混编融合测试")
    void testChildListMixedModifications() {
        DataBlock base156 = buildBaseRecord156();

        // 构造 patch (100% 读写同构):
        // 前端表格编辑后的列表仅包含:
        // 1. 修改行 1 (id=101): 把 phone 改为 "13888888888"，其他属性不传 (保留原 is_primary 等)
        // 2. 新增行 2 (无 id): phone = "13666666666", is_primary = false
        // (原 156 中的行 102 未包含在提交列表中，代表在界面上被用户删除)
        DataRow patchMainRow = new DataRow();

        List<DataRow> patchContactRows = new ArrayList<>();

        // 修改行 101
        DataRow updateRow = new DataRow();
        updateRow.putValue("order_contact", "id", 101L);
        updateRow.putValue("order_contact", "phone", "13888888888");
        patchContactRows.add(updateRow);

        // 新增行
        DataRow newRow = new DataRow();
        newRow.putValue("order_contact", "phone", "13666666666");
        newRow.putValue("order_contact", "is_primary", false);
        patchContactRows.add(newRow);

        DataBlock patchChildBlock = DataBlock.of(200L, patchContactRows);
        patchMainRow.setChildren(Collections.singletonList(patchChildBlock));

        DataBlock patch = DataBlock.of(100L, patchMainRow);

        // 执行深度合并与擦除
        DataBlock merged = TreeMergeUtils.deepMerge(base156, patch);
        TreeMergeUtils.stripIdsAndResetAudit(merged);

        DataRow mainRow = merged.getFirstRecord();
        assertNotNull(mainRow);

        DataBlock finalContactBlock = mainRow.getChild(200L);
        assertNotNull(finalContactBlock);

        // 原 2 条，删 1 条，增 1 条 -> 最终应该是 2 条记录
        assertEquals(2, finalContactBlock.size());

        // 验证第 1 条 (由原 101 打补丁覆盖所得):
        // 电话被修改为 13888888888，但 156 原有的 is_primary = true 得到了完整继承！
        DataRow mergedItem1 = finalContactBlock.getRecords().get(0);
        assertEquals("13888888888", mergedItem1.getString("order_contact", "phone"));
        assertEquals(true, mergedItem1.getValue("order_contact", "is_primary"));
        assertNull(mergedItem1.getValue("order_contact", "id"));

        // 验证第 2 条 (全新新增明细):
        DataRow mergedItem2 = finalContactBlock.getRecords().get(1);
        assertEquals("13666666666", mergedItem2.getString("order_contact", "phone"));
        assertEquals(false, mergedItem2.getValue("order_contact", "is_primary"));
        assertNull(mergedItem2.getValue("order_contact", "id"));
    }

    private DataBlock buildBaseRecord156() {
        DataRow mainRow = new DataRow();
        mainRow.putValue("orders", "id", 156L);
        mainRow.putValue("orders", "name", "张三");
        mainRow.putValue("orders", "order_no", "SO-2026-001");
        mainRow.putValue("orders", "status", "SUBMITTED");
        mainRow.putValue("orders", "deleted", 0);
        mainRow.putValue("orders", "created_at", "2026-10-01 10:00:00");
        mainRow.putValue("orders", "updated_at", "2026-10-02 12:00:00");

        // 子表 contacts (moduleId = 200L)
        List<DataRow> contactRows = new ArrayList<>();

        DataRow c1 = new DataRow();
        c1.putValue("order_contact", "id", 101L);
        c1.putValue("order_contact", "order_id", 156L);
        c1.putValue("order_contact", "phone", "13800000000");
        c1.putValue("order_contact", "is_primary", true);
        c1.putValue("order_contact", "deleted", 0);
        c1.putValue("order_contact", "created_at", "2026-10-01 10:00:00");
        contactRows.add(c1);

        DataRow c2 = new DataRow();
        c2.putValue("order_contact", "id", 102L);
        c2.putValue("order_contact", "order_id", 156L);
        c2.putValue("order_contact", "phone", "13900000000");
        c2.putValue("order_contact", "is_primary", false);
        c2.putValue("order_contact", "deleted", 0);
        c2.putValue("order_contact", "created_at", "2026-10-01 10:05:00");
        contactRows.add(c2);

        DataBlock contactBlock = DataBlock.of(200L, contactRows);
        mainRow.setChildren(Collections.singletonList(contactBlock));

        return DataBlock.of(100L, mainRow);
    }
}
