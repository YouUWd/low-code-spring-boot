package com.jdec.platform.config.biz.service;

import com.jdec.platform.config.api.SysMenuHeaderNodeApi;
import com.jdec.platform.config.biz.mapper.SysMenuHeaderNodeMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 菜单表头节点 Service 实现 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysMenuHeaderNodeService implements SysMenuHeaderNodeApi {

    private final SysMenuHeaderNodeMapper sysMenuHeaderNodeMapper;
}
