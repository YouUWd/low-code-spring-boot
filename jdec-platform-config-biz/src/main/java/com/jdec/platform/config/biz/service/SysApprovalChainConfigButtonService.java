package com.jdec.platform.config.biz.service;

import com.jdec.platform.config.api.SysApprovalChainConfigButtonApi;
import com.jdec.platform.config.biz.mapper.SysApprovalChainConfigButtonMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 审批链配置按钮 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysApprovalChainConfigButtonService implements SysApprovalChainConfigButtonApi {

    private final SysApprovalChainConfigButtonMapper sysApprovalChainConfigButtonMapper;
}
