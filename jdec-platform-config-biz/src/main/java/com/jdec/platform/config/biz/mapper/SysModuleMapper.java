package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysModule;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** 模块基本信息 Mapper 接口 使用配置中心数据源 */
@Mapper
public interface SysModuleMapper extends BaseMapper<SysModule> {

    /**
     * 按 JSON 指定的主键插入，主键由初始化逻辑维护。
     *
     * <p>实体主键策略为 {@code IdType.AUTO}，{@code BaseMapper.insert} 生成的 SQL 不含主键列，无法指定主键，
     * 故初始化单独走此方法；普通新增仍走 {@code insert} 由数据库自增。
     */
    @Insert(
            "INSERT INTO sys_module (id, project_no, subject_id, module_code, module_name, module_desc, category,"
                    + " source_subjects, parent_id, enable_active, change_module_id, sort_order, primary_table, created_date, updated_date) "
                    + "VALUES (#{id}, #{projectNo}, #{subjectId}, #{moduleCode}, #{moduleName}, #{moduleDesc},"
                    + " #{category},"
                    + " #{sourceSubjects,"
                    + " typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler,"
                    + " jdbcType=VARCHAR},"
                    + " #{parentId}, #{enableActive}, #{changeModuleId}, #{sortOrder}, #{primaryTable}, #{createdDate}, #{updatedDate})")
    int insertWithId(SysModule entity);
}
