package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysMenu;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** 系统菜单 Mapper */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 按 JSON 指定的主键插入，主键由初始化逻辑维护。
     *
     * <p>实体主键策略为 {@code IdType.AUTO}，{@code BaseMapper.insert} 生成的 SQL 不含主键列，无法指定主键，
     * 故初始化单独走此方法；普通新增仍走 {@code insert} 由数据库自增。
     */
    @Insert(
            "INSERT INTO sys_menu (id, pid, subject_id, project_no, title, param, menu_type, structural_type,"
                    + " personalize_flag, category, img_default, img_active, module_id, sort_order, showed, enabled,"
                    + " doc_url, doc_name, video_url, video_name, created_date, updated_date) "
                    + "VALUES (#{id}, #{pid}, #{subjectId}, #{projectNo}, #{title}, #{param}, #{menuType},"
                    + " #{structuralType}, #{personalizeFlag}, #{category}, #{imgDefault}, #{imgActive},"
                    + " #{moduleId}, #{sortOrder}, #{showed}, #{enabled}, #{docUrl}, #{docName}, #{videoUrl},"
                    + " #{videoName}, #{createdDate}, #{updatedDate})")
    int insertWithId(SysMenu entity);
}
