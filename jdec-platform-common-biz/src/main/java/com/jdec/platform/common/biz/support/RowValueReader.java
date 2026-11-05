package com.jdec.platform.common.biz.support;

import com.jdec.platform.common.api.dto.response.StatusSimpleResp;
import com.jdec.platform.common.api.dto.response.UserSimpleResp;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.Map;

/**
 * 数据行字段读取工具 (RowValueReader)
 *
 * <p>统一兼容原始值 / 已增强对象 (StatusSimpleResp、UserSimpleResp) / Map 等多种形态的字段读取。
 */
public final class RowValueReader {

    private RowValueReader() {}

    /** 兼容性提取当前行的创建人 ID (created_by / createdBy / create_by / createBy) */
    public static Long extractCreatedBy(DataRow row, String primaryTable) {
        if (row == null) {
            return null;
        }
        Object val = null;
        if (primaryTable != null) {
            val = row.getValue(primaryTable, "created_by");
            if (val == null) {
                val = row.getValue(primaryTable, "createdBy");
            }
            if (val == null) {
                val = row.getValue(primaryTable, "create_by");
            }
            if (val == null) {
                val = row.getValue(primaryTable, "createBy");
            }
        }
        // 若从主表未取到，兼容从顶层 tables 直接取
        if (val == null && row.getTables() != null) {
            val = row.getTables().get("created_by");
            if (val == null) {
                val = row.getTables().get("createdBy");
            }
            if (val == null) {
                val = row.getTables().get("create_by");
            }
            if (val == null) {
                val = row.getTables().get("createBy");
            }
        }
        // 若仍未取到，尝试从各表 Map 中提取
        if (val == null && row.getTables() != null) {
            for (Object obj : row.getTables().values()) {
                if (obj instanceof Map<?, ?> map) {
                    val = map.get("created_by");
                    if (val == null) {
                        val = map.get("createdBy");
                    }
                    if (val == null) {
                        val = map.get("create_by");
                    }
                    if (val == null) {
                        val = map.get("createBy");
                    }
                    if (val != null) {
                        break;
                    }
                }
            }
        }
        return parseUserId(val);
    }

    /** 解析用户 ID，兼容 Number / UserSimpleResp / Map / String */
    public static Long parseUserId(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof UserSimpleResp u) {
            return u.getUserId();
        }
        if (val instanceof Number n) {
            return n.longValue();
        }
        if (val instanceof Map<?, ?> map) {
            Object uid = map.get("userId");
            if (uid == null) {
                uid = map.get("user_id");
            }
            if (uid == null) {
                uid = map.get("id");
            }
            if (uid instanceof Number n) {
                return n.longValue();
            }
            return uid != null ? parseLongQuietly(uid) : null;
        }
        return parseLongQuietly(val);
    }

    /** 兼容性提取当前行的业务状态 (business_status / businessStatus) */
    public static Integer extractBusinessStatus(DataRow row, String primaryTable) {
        if (row == null) {
            return null;
        }
        Object val = null;
        if (primaryTable != null) {
            val = row.getValue(primaryTable, "business_status");
            if (val == null) {
                val = row.getValue(primaryTable, "businessStatus");
            }
        }
        // 若从主表未取到，兼容从顶层 tables 直接取
        if (val == null && row.getTables() != null) {
            val = row.getTables().get("business_status");
            if (val == null) {
                val = row.getTables().get("businessStatus");
            }
        }
        // 若仍未取到，尝试从各表 Map 中提取
        if (val == null && row.getTables() != null) {
            for (Object obj : row.getTables().values()) {
                if (obj instanceof Map<?, ?> map) {
                    val = map.get("business_status");
                    if (val == null) {
                        val = map.get("businessStatus");
                    }
                    if (val != null) {
                        break;
                    }
                }
            }
        }
        return parseStatusValue(val);
    }

    /** 解析状态值，兼容 Number / StatusSimpleResp / Map / String */
    public static Integer parseStatusValue(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof StatusSimpleResp s) {
            return s.getStatusValue();
        }
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof Map<?, ?> map) {
            Object sv = map.get("statusValue");
            if (sv == null) {
                sv = map.get("status_value");
            }
            if (sv instanceof Number n) {
                return n.intValue();
            }
            return sv != null ? parseIntQuietly(sv) : null;
        }
        return parseIntQuietly(val);
    }

    private static Integer parseIntQuietly(Object val) {
        try {
            return Integer.parseInt(String.valueOf(val).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLongQuietly(Object val) {
        try {
            return Long.parseLong(String.valueOf(val).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
