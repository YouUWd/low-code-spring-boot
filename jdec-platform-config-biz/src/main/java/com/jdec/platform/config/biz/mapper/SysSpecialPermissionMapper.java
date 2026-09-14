package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysSpecialPermission;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** 特殊权限 Mapper */
@Mapper
public interface SysSpecialPermissionMapper extends BaseMapper<SysSpecialPermission> {

    /**
     * 按 JSON 指定的主键插入，主键由初始化逻辑维护。
     *
     * <p>实体主键策略为 {@code IdType.AUTO}，{@code BaseMapper.insert} 生成的 SQL 不含主键列，无法指定主键，
     * 故初始化单独走此方法；普通新增仍走 {@code insert} 由数据库自增。
     */
    @Insert(
            "INSERT INTO sys_special_permission (id, code, name, description, sort, status, subject_id,"
                    + " project_no, created_date, updated_date) "
                    + "VALUES (#{id}, #{code}, #{name}, #{description}, #{sort}, #{status}, #{subjectId},"
                    + " #{projectNo}, #{createdDate}, #{updatedDate})")
    int insertWithId(SysSpecialPermission entity);
}
