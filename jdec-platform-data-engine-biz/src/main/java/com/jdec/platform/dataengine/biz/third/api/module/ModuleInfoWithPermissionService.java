package com.jdec.platform.dataengine.biz.third.api.module;

import com.jdec.platform.dataengine.biz.dto.FormButton;
import com.jdec.platform.dataengine.biz.dto.ModuleFilterInfo;
import java.util.Set;

/** 模块权限相关，获取过滤权限后的模块信息，获取模块对应的编辑或查看权限，构造列表操作按钮 * */
public interface ModuleInfoWithPermissionService {

    /**
     * 获取模块权限、互动权限、及按钮 调用组件: 系统配置： /api/config/modules/complete/{moduleId}
     * /api/config/tables/{tableName}/columns/simple-settings /api/config/modules/tree
     * /api/config/sys/module-field-permission/role-permissions
     * /api/config/sys/role/get-role-interaction-permission-by-module
     *
     * @param subjectId 主体ID
     * @param roleId 角色ID
     * @param moduleId 模块ID
     * @param btnType 创建时都传create，查询对应的创建权限。 查看时不带审批链传空，根据当前模块以及下属子模块权限判断btnType是view或edit，并返回对应的权限。
     *     带审批链时btnType不能为空，传入view或者edit，按指定的btnType返回对应权限。
     * @param withApproval 是否带审批链
     * @return
     */
    ModuleFilterInfo filterModuleInfo(
            Long subjectId, Long roleId, Long moduleId, String btnType, boolean withApproval);

    /**
     * 获取列表操作列按钮列表，不带审批链时按钮为顶层模块(详情)及下属模块的权限综合判断，其中某一个模块(非详情中的列表时排除弹窗模块)有编辑权限，按钮就为编辑，都没有编辑节点则为查看
     * 带审批链的列表，按钮列表从审批链接口进行查询 调用组件: 系统配置： /api/config/modules/tree
     * /api/config/sys/module-field-permission/role-permissions
     *
     * @param subjectId 主体ID
     * @param roleId 角色ID
     * @param moduleId 模块ID
     * @param btnType 创建时都传create，查询对应的创建权限。 查看时不带审批链传空，根据当前模块以及下属子模块权限判断btnType是view或edit，并返回对应的权限。
     *     带审批链时btnType不能为空，传入view或者edit，按指定的btnType返回对应权限。
     * @param withApproval 是否带审批链
     * @return
     */
    Set<FormButton> getListOperateBtn(
            Long subjectId, Long roleId, Long moduleId, String btnType, boolean withApproval);

    /**
     * 获取列表模块的新建按钮，无新建权限时返回空。 调用组件: 系统配置： /api/config/sys/module-field-permission/role-permissions
     *
     * @param subjectId 主体ID
     * @param roleId 角色ID
     * @param moduleId 模块ID
     * @return
     */
    FormButton getCreateBtn(Long subjectId, Long roleId, Long moduleId);

    /**
     * 获取详情底部操作按钮列表， 不带审批链时按钮为顶层模块及下属模块的权限综合判断，其中某一个模块(非详情中的列表时排除弹窗模块)有编辑权限，按钮就为保存，否则为空(查看模式)。
     * 带审批链的列表，按钮列表从审批链接口进行查询 调用组件: 系统配置： /api/config/modules/tree
     * /api/config/sys/module-field-permission/role-permissions
     *
     * @param subjectId 主体ID
     * @param roleId 角色ID
     * @param moduleId 模块ID
     * @param btnType 创建时都传create，查询对应的创建权限。 查看时不带审批链传空，根据当前模块以及下属子模块权限判断btnType是view或edit，并返回对应的权限。
     *     带审批链时btnType不能为空，传入view或者edit，按指定的btnType返回对应权限。
     * @param withApproval 是否带审批链
     * @return
     */
    Set<FormButton> getDeatailBottomBtn(
            Long subjectId, Long roleId, Long moduleId, String btnType, boolean withApproval);

    /**
     * 根据模块中主表和子表的关联关系，填充补齐角色模块权限中未勾选(查看也没有)，但是页面显示、关联查询、详情查询时需要用到的主键ID、关联外键ID字段，将补齐的字段权限节点增加到simpleModuleFields
     * 补齐规则:字段勾选了权限节点，不做操作；未出现在simpleModuleFields中(查看都没)，则手动增加该字段权限，permissionCode设置为3
     *
     * @param moduleFilterInfo 模块信息
     */
    void fillIdAndJoinIdField(ModuleFilterInfo moduleFilterInfo);
}
