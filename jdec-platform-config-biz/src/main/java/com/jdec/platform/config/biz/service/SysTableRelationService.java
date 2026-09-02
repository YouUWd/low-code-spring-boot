package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysTableRelationApi;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.biz.entity.SysTableRelation;
import com.jdec.platform.config.biz.mapper.SysTableRelationMapper;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 全局物理表关联关系服务实现 */
@Service
@RequiredArgsConstructor
@Slf4j
@com.jdec.platform.shared.datasource.DataSource(DataSourceConstants.CONFIG_ENGINE)
public class SysTableRelationService implements SysTableRelationApi {

    private final SysTableRelationMapper sysTableRelationMapper;

    @Override
    public List<TableRelationDTO> listRelations(String projectNo, Long subjectId) {
        LambdaQueryWrapper<SysTableRelation> wrapper = Wrappers.lambdaQuery();
        if (projectNo != null && !projectNo.isBlank()) {
            wrapper.eq(SysTableRelation::getProjectNo, projectNo);
        }
        if (subjectId != null && subjectId > 0) {
            wrapper.eq(SysTableRelation::getSubjectId, subjectId);
        }
        wrapper.orderByAsc(SysTableRelation::getMainTable)
                .orderByAsc(SysTableRelation::getJoinTable);

        List<SysTableRelation> list = sysTableRelationMapper.selectList(wrapper);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Long saveRelation(String projectNo, Long subjectId, TableRelationDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "关联配置信息不能为空");
        }
        if (dto.getMainTable() == null || dto.getMainTable().isBlank()) {
            throw new BusinessException(400, "主表名不能为空");
        }
        if (dto.getJoinTable() == null || dto.getJoinTable().isBlank()) {
            throw new BusinessException(400, "被关联表名不能为空");
        }
        if (dto.getRelationType() == null || dto.getRelationType().isBlank()) {
            throw new BusinessException(400, "关联类型不能为空");
        }

        SysTableRelation entity;
        if (dto.getId() != null) {
            entity = sysTableRelationMapper.selectById(dto.getId());
            if (entity == null) {
                throw new BusinessException(404, "关联记录不存在");
            }
        } else {
            entity = new SysTableRelation();
            entity.setProjectNo(projectNo);
            entity.setSubjectId(subjectId);
            entity.setCreatedDate(LocalDateTime.now());
        }

        entity.setMainTable(dto.getMainTable().trim());
        entity.setMainField(
                (dto.getMainField() != null && !dto.getMainField().isBlank())
                        ? dto.getMainField().trim()
                        : "id");
        entity.setJoinTable(dto.getJoinTable().trim());
        entity.setJoinField(
                (dto.getJoinField() != null && !dto.getJoinField().isBlank())
                        ? dto.getJoinField().trim()
                        : "id");
        entity.setRelationType(dto.getRelationType().trim().toUpperCase());
        entity.setDescription(dto.getDescription());
        entity.setUpdatedDate(LocalDateTime.now());

        if (dto.getId() != null) {
            sysTableRelationMapper.updateById(entity);
        } else {
            sysTableRelationMapper.insert(entity);
        }

        return entity.getId();
    }

    @Override
    @Transactional
    public void deleteRelation(Long id) {
        if (id == null) return;
        sysTableRelationMapper.deleteById(id);
    }

    private TableRelationDTO toDTO(SysTableRelation entity) {
        if (entity == null) return null;
        return TableRelationDTO.builder()
                .id(entity.getId())
                .mainTable(entity.getMainTable())
                .mainField(entity.getMainField())
                .joinTable(entity.getJoinTable())
                .joinField(entity.getJoinField())
                .relationType(entity.getRelationType())
                .description(entity.getDescription())
                .build();
    }
}
