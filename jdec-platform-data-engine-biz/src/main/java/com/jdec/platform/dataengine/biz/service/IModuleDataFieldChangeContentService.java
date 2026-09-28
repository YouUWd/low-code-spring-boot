package com.jdec.platform.dataengine.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.dataengine.biz.entity.ModuleDataFieldChangeContent;
import java.util.List;

/**
 * 模块数据变更记录 服务类
 *
 * @author
 * @since 2026-09-16
 */
public interface IModuleDataFieldChangeContentService {

    public int batchInsert(List<ModuleDataFieldChangeContent> list);

    public int batchUpdate(List<ModuleDataFieldChangeContent> list);

    public ModuleDataFieldChangeContent queryOne(
            LambdaQueryWrapper<ModuleDataFieldChangeContent> wrapper);

    public List<ModuleDataFieldChangeContent> queryList(
            LambdaQueryWrapper<ModuleDataFieldChangeContent> wrapper);
}
