package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.api.SysModuleTableApi;
import com.jdec.platform.config.api.dto.request.CreateSysModuleTableReq;
import com.jdec.platform.config.api.dto.request.QuerySysModuleTableReq;
import com.jdec.platform.config.api.dto.request.UpdateSysModuleTableReq;
import com.jdec.platform.config.api.dto.response.SysModuleTableDetailResp;
import com.jdec.platform.config.api.dto.response.SysModuleTableEntryResp;
import com.jdec.platform.config.biz.entity.SysModuleTable;
import com.jdec.platform.config.biz.mapper.SysModuleTableMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模块表管理 Service 实现
 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysModuleTableService implements SysModuleTableApi {

    private final SysModuleTableMapper sysModuleTableMapper;

    @Override
    public SysModuleTableEntryResp createModuleTable(CreateSysModuleTableReq request) {
        SysModuleTable table = new SysModuleTable();
        table.setModuleId(request.getModuleId());
        table.setTableName(request.getTableName());
        table.setTableDesc(request.getTableDesc());
        table.setJoinLeftField(request.getJoinLeftField());
        table.setJoinRightField(request.getJoinRightField());
        table.setRelationType(request.getRelationType());
        table.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        table.setCreatedBy(request.getCreatedBy());
        table.setCreatedDate(LocalDateTime.now());
        table.setCreatedName(request.getCreatedName());
        table.setDeleted(0);

        sysModuleTableMapper.insert(table);
        return toModuleTableEntry(table);
    }

    @Override
    public SysModuleTableDetailResp getModuleTableById(Long tableId) {
        SysModuleTable table = sysModuleTableMapper.selectById(tableId);
        return toModuleTableDetail(table);
    }

    @Override
    public List<SysModuleTableEntryResp> getTablesByModule(Long moduleId) {
        LambdaQueryWrapper<SysModuleTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModuleTable::getModuleId, moduleId)
                .eq(SysModuleTable::getDeleted, 0)
                .orderByAsc(SysModuleTable::getSortOrder);

        List<SysModuleTable> tables = sysModuleTableMapper.selectList(wrapper);
        return tables.stream()
                .map(this::toModuleTableEntry)
                .collect(Collectors.toList());
    }

    @Override
    public List<SysModuleTableEntryResp> queryModuleTables(QuerySysModuleTableReq request) {
        LambdaQueryWrapper<SysModuleTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModuleTable::getModuleId, request.getModuleId())
                .eq(SysModuleTable::getDeleted, 0);

        if (request.getTableName() != null) {
            wrapper.like(SysModuleTable::getTableName, request.getTableName());
        }
        if (request.getRelationType() != null) {
            wrapper.eq(SysModuleTable::getRelationType, request.getRelationType());
        }

        wrapper.orderByAsc(SysModuleTable::getSortOrder);
        List<SysModuleTable> tables = sysModuleTableMapper.selectList(wrapper);
        return tables.stream()
                .map(this::toModuleTableEntry)
                .collect(Collectors.toList());
    }

    @Override
    public SysModuleTableEntryResp updateModuleTable(UpdateSysModuleTableReq request) {
        SysModuleTable table = sysModuleTableMapper.selectById(request.getId());
        if (table == null) {
            throw new RuntimeException("表不存在");
        }

        table.setTableName(request.getTableName());
        table.setTableDesc(request.getTableDesc());
        table.setJoinLeftField(request.getJoinLeftField());
        table.setJoinRightField(request.getJoinRightField());
        table.setRelationType(request.getRelationType());
        table.setSortOrder(request.getSortOrder());
        table.setUpdatedBy(request.getUpdatedBy());
        table.setUpdatedDate(LocalDateTime.now());
        table.setUpdatedName(request.getUpdatedName());

        sysModuleTableMapper.updateById(table);
        return toModuleTableEntry(table);
    }

    @Override
    public void deleteModuleTable(Long tableId) {
        SysModuleTable table = sysModuleTableMapper.selectById(tableId);
        if (table != null) {
            table.setDeleted(1);
            sysModuleTableMapper.updateById(table);
        }
    }

    @Override
    public void batchDeleteModuleTables(List<Long> tableIds) {
        if (CollectionUtils.isEmpty(tableIds)) {
            return;
        }
        LambdaQueryWrapper<SysModuleTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysModuleTable::getId, tableIds);
        List<SysModuleTable> tables = sysModuleTableMapper.selectList(wrapper);
        tables.forEach(table -> {
            table.setDeleted(1);
            sysModuleTableMapper.updateById(table);
        });
    }

    private SysModuleTableEntryResp toModuleTableEntry(SysModuleTable table) {
        SysModuleTableEntryResp resp = new SysModuleTableEntryResp();
        resp.setId(table.getId());
        resp.setModuleId(table.getModuleId());
        resp.setTableName(table.getTableName());
        resp.setRelationType(table.getRelationType());
        resp.setSortOrder(table.getSortOrder());
        resp.setCreatedDate(table.getCreatedDate());
        resp.setUpdatedDate(table.getUpdatedDate());
        return resp;
    }

    private SysModuleTableDetailResp toModuleTableDetail(SysModuleTable table) {
        if (table == null) {
            return null;
        }
        SysModuleTableDetailResp resp = new SysModuleTableDetailResp();
        resp.setId(table.getId());
        resp.setModuleId(table.getModuleId());
        resp.setTableName(table.getTableName());
        resp.setTableDesc(table.getTableDesc());
        resp.setJoinLeftField(table.getJoinLeftField());
        resp.setJoinRightField(table.getJoinRightField());
        resp.setRelationType(table.getRelationType());
        resp.setSortOrder(table.getSortOrder());
        resp.setCreatedBy(table.getCreatedBy());
        resp.setCreatedDate(table.getCreatedDate());
        resp.setCreatedName(table.getCreatedName());
        resp.setUpdatedBy(table.getUpdatedBy());
        resp.setUpdatedDate(table.getUpdatedDate());
        resp.setUpdatedName(table.getUpdatedName());
        return resp;
    }
}
