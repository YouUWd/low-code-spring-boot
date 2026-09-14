package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysButton;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysButtonMapper extends BaseMapper<SysButton> {

    /**
     * 按 JSON 指定的主键插入，主键由初始化逻辑维护。
     *
     * <p>实体主键策略为 {@code IdType.AUTO}，{@code BaseMapper.insert} 生成的 SQL 不含主键列，无法指定主键，
     * 故初始化单独走此方法；普通新增仍走 {@code insert} 由数据库自增。
     */
    @Insert(
            "INSERT INTO sys_button (id, title, alias, type, description, default_bg_color, default_font_color,"
                    + " default_border, levitate_bg_color, levitate_font_color, levitate_border_color,"
                    + " selected_bg_color, selected_font_color, selected_border_color, icon, log_name, sort_order,"
                    + " showed, enabled, subject_id, project_no, created_date, updated_date, deleted) "
                    + "VALUES (#{id}, #{title}, #{alias}, #{type}, #{description}, #{defaultBgColor},"
                    + " #{defaultFontColor}, #{defaultBorder}, #{levitateBgColor}, #{levitateFontColor},"
                    + " #{levitateBorderColor}, #{selectedBgColor}, #{selectedFontColor}, #{selectedBorderColor},"
                    + " #{icon}, #{logName}, #{sortOrder}, #{showed}, #{enabled}, #{subjectId}, #{projectNo},"
                    + " #{createdDate}, #{updatedDate}, 0)")
    int insertWithId(SysButton entity);
}
