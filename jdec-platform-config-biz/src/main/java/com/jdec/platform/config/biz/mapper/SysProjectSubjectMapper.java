package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysProjectSubject;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** 项目主体关联 Mapper */
@Mapper
public interface SysProjectSubjectMapper extends BaseMapper<SysProjectSubject> {

    /**
     * 按 JSON 指定的主键插入，主键由初始化逻辑维护。
     *
     * <p>实体主键策略为 {@code IdType.AUTO}，{@code BaseMapper.insert} 生成的 SQL 不含主键列，无法指定主键，
     * 故初始化单独走此方法；普通新增仍走 {@code insert} 由数据库自增。
     */
    @Insert(
            "INSERT INTO sys_project_subject (id, subject_name, company_name, alias, subject_id, project_no) "
                    + "VALUES (#{id}, #{subjectName}, #{companyName}, #{alias}, #{subjectId}, #{projectNo})")
    int insertWithId(SysProjectSubject entity);
}
