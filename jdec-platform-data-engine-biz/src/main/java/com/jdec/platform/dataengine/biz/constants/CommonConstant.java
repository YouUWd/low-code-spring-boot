package com.jdec.platform.dataengine.biz.constants;

import java.util.Map;
import java.util.Set;

public interface CommonConstant {

    String START_KEY = "start";

    String END_KEY = "end";

    public final Set<String> ORDER_ASC_SET = Set.of("asc", "ascend");

    String REDIS_LOGIN_PREFIX = "HR:login:";

    String REDIS_LOGIN_CURRENT_PREFIX = "HR:login:current:";

    String REDIS_HASH_KEY_MOCK_ROLE = "mock_role";

    String REDIS_HASH_KEY_CURRENT_ROLE = "current_role";

    String SYS_CONFIG_CATEGORY_ALIAS_USER_OP_LIST = "userOpList";

    String SYS_CONFIG_BTN_VIEW = "view";
    String SYS_CONFIG_BTN_MODIFY = "modify";
    String SYS_CONFIG_BTN_EDIT = "edit";
    String SYS_CONFIG_BTN_CREATE = "create";

    String SPLITTER_PATTERN = "[,|，|、|.]";

    String ORG_TREE_CACHE_KEY_PREFIX = "HR:org:tree:";

    String USER_CACHE_KEY_PREFIX = "HR:user:";

    String ORG_CACHE_KEY_PREFIX = "Organization:";

    String DIC_NAME_PATH_SPLITTER = "@#@";

    Integer CONFIG_DEFAULT_MENU_CATEGORY = 1;

    Integer CONFIG_MODULE_TABLE_READ_ONLY = 1;

    Map<Long, String> SUBJECT_MAP = Map.of(1L, "环保", 2L, "电气", 3L, "能化");

    String USER_CURRENT_ROLE_REDIS_KEY_PREFIX = "jdec:student:user_current_role:%s";

    String CONFIG_CENTER_REDIS_CHANNEL_KEY = "jdec:config:change:%s:%s:%s";

    String AUTHORIZATION_HEADER = "Authorization";

    String BEARER_PREFIX = "Bearer ";

    String CONFIG_CENTER_SIMULATE_LOGIN_RIGHT_CODE = "simulate_login";

    String CONFIG_CENTER_CATEGORY_ALIAS_WX_ACCEPT_TYPE = "wxAcceptType";

    String MONITOR_LOG_OPERATOR_LOG_TYPE = "operation-log";

    String TABLE_DEFAULT_PRIMARY_KEY_FIELD = "id";

    String TABLE_DEFAULT_DELETED_FIELD = "deleted";
}
