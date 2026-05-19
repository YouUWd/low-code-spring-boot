package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.config.biz.entity.SysRight;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 权限 Mapper */
@Mapper
public interface SysRightMapper extends BaseMapper<SysRight> {

    /**
     * 分页查询权限节点（联合三张表）
     *
     * @param page 分页对象
     * @param projectNo 项目编号
     * @param subjectId 主体ID
     * @param rightName 权限节点名称（模糊查询）
     * @return 分页结果
     */
    Page<SysRightResp> selectRightPage(
            Page<SysRightResp> page,
            @Param("projectNo") String projectNo,
            @Param("subjectId") Long subjectId,
            @Param("rightName") String rightName);
}
