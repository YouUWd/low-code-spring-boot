package com.jdec.platform.common.biz.postprocess.action.interaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.common.api.dto.response.ActionButtonDTO;
import com.jdec.platform.config.api.dto.response.RoleInteractionPermissionByModuleResp.InteractionPermissionItem;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import com.jdec.platform.validation.externalobject.ExternalObjectValues;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 模块 COMPANY-ROOT (外部对象管理) 互动权限定制提供者 (CompanyInteractionButtonProvider)
 *
 * <p>核心契约：
 *
 * <ul>
 *   <li>目标模块标识：支持使用 {@code '|'} 分隔多个模块编码（如 {@code COMPANY-ROOT|WBDX}）；
 *   <li>继承自 {@link DefaultInteractionButtonProvider}，默认继承通用全局按钮能力；
 *   <li>在全局按钮中排除 {@code edit_company_reg_status}，定制业务在其本身的 Provider ({@link #addRowButtons}) 中独立处理；
 *   <li>声明 {@link #requiredRowFields()} 依赖 {@code nature} 物理列，由查询引擎在查询阶段统一补充投影并在返回前端时剥离；
 *   <li>定制业务规则：只有当前角色具备权限且单据处于【启用状态 (99)】或【取消状态 (-99)】时，且对象性质 (nature) 非【个人】时，才为该行添加修改登记状态按钮；
 *   <li>纯内存高速判定，彻底杜绝 N+1 数据库反查。
 * </ul>
 */
@Slf4j
@Component
@Order(10)
public class CompanyInteractionButtonProvider extends DefaultInteractionButtonProvider {

    /** 目标模块标识：支持 '|' 分隔多个模块编码（如 COMPANY-ROOT|WBDX） */
    public static final String MODULE_CODE_COMPANY_ROOT = "COMPANY-ROOT|WBDX";

    /** 目标互动权限编码：修改登记状态 */
    public static final String PERMISSION_EDIT_COMPANY_REG_STATUS = "edit_company_reg_status";

    /** 业务状态：启用 (99) */
    public static final int STATUS_ACTIVE = 99;

    /** 业务状态：停用 (-2) */
    public static final int STATUS_INACTIVE = -2;

    private final ObjectMapper objectMapper;

    public CompanyInteractionButtonProvider() {
        this((ObjectMapper) null);
    }

    @Autowired
    public CompanyInteractionButtonProvider(@Nullable ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /** 兼容历史构造器（不再依赖 DataSourceResolver 反查） */
    @Deprecated
    public CompanyInteractionButtonProvider(
            @Nullable DataSourceResolver dataSourceResolver, @Nullable ObjectMapper objectMapper) {
        this(objectMapper);
    }

    @Override
    public String target() {
        return MODULE_CODE_COMPANY_ROOT;
    }

    /** 声明计算当前行按钮所需的行级依赖列，查询引擎将在底层查询前自动补齐并在返回时剥离 */
    @Override
    public Set<String> requiredRowFields() {
        return Set.of("nature");
    }

    /** 将 edit_company_reg_status 排除在全局按钮之外，转由本 Provider 自行按行定制处理 */
    @Override
    protected boolean isRowLevelPermission(
            InteractionPermissionItem perm, InteractionActionContext ctx) {
        if (perm != null && PERMISSION_EDIT_COMPANY_REG_STATUS.equals(perm.getPermissionCode())) {
            return true;
        }
        return super.isRowLevelPermission(perm, ctx);
    }

    /**
     * 定制业务在本身 Provider 中处理：
     *
     * <p>仅当当前角色拥有权限，单据处于【启用 (99)】或【停用 (-2)】状态，且对象性质 (nature) 非【个人】时，为该行添加修改登记状态按钮。
     */
    @Override
    public void addRowButtons(
            DataRow row, InteractionActionContext ctx, List<ActionButtonDTO> rowBtns) {
        if (row == null || ctx == null || rowBtns == null) {
            return;
        }

        // 1. 检查当前角色是否拥有修改登记状态权限
        if (!ctx.hasPermission(PERMISSION_EDIT_COMPANY_REG_STATUS)) {
            return;
        }

        // 2. 定制业务规则：只有处于启用 (99) 或取消 (-99) 状态的单据才允许修改登记状态
        Integer businessStatus = extractBusinessStatus(row, ctx);
        if (businessStatus == null
                || (businessStatus != STATUS_ACTIVE && businessStatus != STATUS_INACTIVE)) {
            return;
        }

        // 3. 定制业务规则：如果当前行的对象性质 (nature) 是“个人”，则不显示修改登记状态按钮
        if (isIndividualRow(row, ctx)) {
            return;
        }

        InteractionPermissionItem perm = findPermission(ctx, PERMISSION_EDIT_COMPANY_REG_STATUS);
        String name =
                (perm != null && perm.getPermissionName() != null)
                        ? perm.getPermissionName()
                        : "外部对象修改登记状态";
        Integer type = perm != null ? perm.getType() : 2;
        Integer sort = perm != null ? perm.getSort() : null;
        rowBtns.add(ActionButtonDTO.of(PERMISSION_EDIT_COMPANY_REG_STATUS, name, type, sort));
    }

    /**
     * 判断当前行对应的外部对象性质是否为“个人”
     *
     * <p>查询阶段已统一补齐影子投影列 (nature)，此处直接从行数据提取，纯内存判定，杜绝 N+1 反查。
     */
    protected boolean isIndividualRow(DataRow row, InteractionActionContext ctx) {
        String primaryTable =
                (ctx != null && StringUtils.hasText(ctx.getPrimaryTable()))
                        ? ctx.getPrimaryTable().trim()
                        : "info";

        String nature = extractNatureFromRow(row, primaryTable);
        return ExternalObjectValues.isIndividualNature(nature);
    }

    private String extractNatureFromRow(DataRow row, String primaryTable) {
        if (row == null) {
            return null;
        }
        Object val = null;
        if (primaryTable != null) {
            val = row.getValue(primaryTable, "nature");
        }
        if (val == null && row.getTables() != null) {
            val = row.getTables().get("nature");
        }
        if (val == null && row.getTables() != null) {
            for (Object obj : row.getTables().values()) {
                if (obj instanceof Map<?, ?> map) {
                    val = map.get("nature");
                    if (val != null) {
                        break;
                    }
                }
            }
        }
        return toText(val);
    }

    private String toText(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof String s) {
            return s.trim();
        }
        try {
            return objectMapper.writeValueAsString(val);
        } catch (Exception e) {
            return String.valueOf(val);
        }
    }
}
