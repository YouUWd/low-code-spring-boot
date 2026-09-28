package com.jdec.platform.dataengine.biz.dto.context;

import lombok.Data;
import lombok.experimental.SuperBuilder;

/** 用于构建SQL 查询条件的 参数对象 */
@SuperBuilder
@Data
public class ConditionContext extends StatementContext {}
