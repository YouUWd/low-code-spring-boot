package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.SysButtonMenuQueryReq;
import com.jdec.platform.config.api.dto.request.SysButtonPageReq;
import com.jdec.platform.config.api.dto.request.SysButtonSaveReq;
import com.jdec.platform.config.api.dto.response.SysButtonMenuGroupResp;
import com.jdec.platform.config.api.dto.response.SysButtonResp;
import com.jdec.platform.shared.model.PageResult;
import java.util.List;

public interface SysButtonApi {

    /** 分页查询按钮列表 */
    PageResult<SysButtonResp> page(SysButtonPageReq req);

    /** 查询所有启用的按钮（不分页） */
    List<SysButtonResp> list();

    /** 按菜单 + 类型批量查询已启用的按钮（仅返回前端需要的字段），并按菜单分组返回 */
    List<SysButtonMenuGroupResp> queryButtonsByMenu(List<SysButtonMenuQueryReq> req);

    /** 新增/编辑按钮 */
    void saveButton(SysButtonSaveReq req);

    /** 删除按钮 */
    void deleteButton(Long id);

    /** 根据按钮ID获取按钮配置 */
    SysButtonResp getButtonById(Long id);

    /** 根据按钮类型获取按钮配置 */
    SysButtonResp getButtonByType(String btnType);
}
