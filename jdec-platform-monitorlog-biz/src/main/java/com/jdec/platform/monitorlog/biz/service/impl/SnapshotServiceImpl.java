package com.jdec.platform.monitorlog.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.api.dto.response.RoleConfigDetailResp;
import com.jdec.platform.config.api.dto.response.UserDetailResp;
import com.jdec.platform.monitorlog.api.client.MonitorLogApiClient;
import com.jdec.platform.monitorlog.biz.entity.JdecMonitorRoleSnapshot;
import com.jdec.platform.monitorlog.biz.entity.JdecMonitorUserSnapshot;
import com.jdec.platform.monitorlog.biz.mapper.JdecMonitorRoleSnapshotMapper;
import com.jdec.platform.monitorlog.biz.mapper.JdecMonitorUserSnapshotMapper;
import com.jdec.platform.monitorlog.biz.service.SnapshotService;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.model.ApiResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.MonitorLog)
public class SnapshotServiceImpl implements SnapshotService {

    private final JdecMonitorUserSnapshotMapper userSnapshotMapper;
    private final JdecMonitorRoleSnapshotMapper roleSnapshotMapper;
    private final MonitorLogApiClient monitorLogApiClient;

    @Override
    public UserDetailResp getUserSnapshot(Long userId) {
        if (userId == null || userId == 0L) {
            return new UserDetailResp();
        }

        LocalDate today = LocalDate.now();

        JdecMonitorUserSnapshot todaySnapshot =
                userSnapshotMapper.selectOne(
                        new LambdaQueryWrapper<JdecMonitorUserSnapshot>()
                                .eq(JdecMonitorUserSnapshot::getUserId, userId)
                                .eq(JdecMonitorUserSnapshot::getSnapshotDate, today));

        if (todaySnapshot != null) {
            return convertToUserDetailResp(todaySnapshot);
        }

        JdecMonitorUserSnapshot latestSnapshot =
                userSnapshotMapper.selectOne(
                        new LambdaQueryWrapper<JdecMonitorUserSnapshot>()
                                .eq(JdecMonitorUserSnapshot::getUserId, userId)
                                .orderByDesc(JdecMonitorUserSnapshot::getSnapshotDate)
                                .last("LIMIT 1"));

        ApiResponse<UserDetailResp> apiResponse = monitorLogApiClient.getUserDetailByUserId(userId);
        UserDetailResp apiUserDetail =
                apiResponse != null && apiResponse.getStatus() == 200
                        ? apiResponse.getData()
                        : null;

        if (apiUserDetail == null || !StringUtils.hasText(apiUserDetail.getUserName())) {
            return latestSnapshot != null
                    ? convertToUserDetailResp(latestSnapshot)
                    : new UserDetailResp();
        }

        boolean needNewSnapshot =
                latestSnapshot == null || isUserInfoChanged(latestSnapshot, apiUserDetail);

        if (needNewSnapshot) {
            saveUserSnapshot(userId, apiUserDetail);
        }

        return apiUserDetail;
    }

    @Override
    public RoleConfigDetailResp getRoleSnapshot(Long roleId) {
        if (roleId == null || roleId == 0L) {
            return new RoleConfigDetailResp();
        }

        LocalDate today = LocalDate.now();

        JdecMonitorRoleSnapshot todaySnapshot =
                roleSnapshotMapper.selectOne(
                        new LambdaQueryWrapper<JdecMonitorRoleSnapshot>()
                                .eq(JdecMonitorRoleSnapshot::getRoleId, roleId)
                                .eq(JdecMonitorRoleSnapshot::getSnapshotDate, today));

        if (todaySnapshot != null) {
            return convertToRoleConfigDetailResp(todaySnapshot);
        }

        JdecMonitorRoleSnapshot latestSnapshot =
                roleSnapshotMapper.selectOne(
                        new LambdaQueryWrapper<JdecMonitorRoleSnapshot>()
                                .eq(JdecMonitorRoleSnapshot::getRoleId, roleId)
                                .orderByDesc(JdecMonitorRoleSnapshot::getSnapshotDate)
                                .last("LIMIT 1"));

        ApiResponse<RoleConfigDetailResp> apiResponse =
                monitorLogApiClient.getRoleConfigDetail(roleId);
        RoleConfigDetailResp apiRoleDetail =
                apiResponse != null && apiResponse.getStatus() == 200
                        ? apiResponse.getData()
                        : null;

        if (apiRoleDetail == null || !StringUtils.hasText(apiRoleDetail.getRoleName())) {
            return latestSnapshot != null
                    ? convertToRoleConfigDetailResp(latestSnapshot)
                    : new RoleConfigDetailResp();
        }

        boolean needNewSnapshot =
                latestSnapshot == null || isRoleInfoChanged(latestSnapshot, apiRoleDetail);

        if (needNewSnapshot) {
            saveRoleSnapshot(roleId, apiRoleDetail);
        }

        return apiRoleDetail;
    }

    @Override
    public void refreshUserSnapshot(Long userId) {
        if (userId == null || userId == 0L) {
            return;
        }

        ApiResponse<UserDetailResp> apiResponse = monitorLogApiClient.getUserDetailByUserId(userId);
        UserDetailResp apiUserDetail =
                apiResponse != null && apiResponse.getStatus() == 200
                        ? apiResponse.getData()
                        : null;
        if (apiUserDetail != null && StringUtils.hasText(apiUserDetail.getUserName())) {
            saveUserSnapshot(userId, apiUserDetail);
        }
    }

    @Override
    public void refreshRoleSnapshot(Long roleId) {
        if (roleId == null || roleId == 0L) {
            return;
        }

        ApiResponse<RoleConfigDetailResp> apiResponse =
                monitorLogApiClient.getRoleConfigDetail(roleId);
        RoleConfigDetailResp apiRoleDetail =
                apiResponse != null && apiResponse.getStatus() == 200
                        ? apiResponse.getData()
                        : null;
        if (apiRoleDetail != null && StringUtils.hasText(apiRoleDetail.getRoleName())) {
            saveRoleSnapshot(roleId, apiRoleDetail);
        }
    }

    private boolean isUserInfoChanged(JdecMonitorUserSnapshot snapshot, UserDetailResp apiDetail) {
        return !equals(snapshot.getUserAvatar(), apiDetail.getUserAvatar())
                || !equals(snapshot.getUserName(), apiDetail.getUserName())
                || !equals(snapshot.getWorkNumber(), apiDetail.getWorkNumber());
    }

    private boolean isRoleInfoChanged(
            JdecMonitorRoleSnapshot snapshot, RoleConfigDetailResp apiDetail) {
        return !equals(snapshot.getRoleName(), apiDetail.getRoleName());
    }

    private boolean equals(String a, String b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    private void saveUserSnapshot(Long userId, UserDetailResp userDetail) {
        JdecMonitorUserSnapshot snapshot = new JdecMonitorUserSnapshot();
        snapshot.setUserId(userId);
        snapshot.setUserAvatar(userDetail.getUserAvatar());
        snapshot.setUserName(userDetail.getUserName());
        snapshot.setWorkNumber(userDetail.getWorkNumber());
        snapshot.setSnapshotDate(LocalDate.now());
        snapshot.setCreatedTime(LocalDateTime.now());

        userSnapshotMapper.insert(snapshot);
        log.debug("保存用户快照: userId={}", userId);
    }

    private void saveRoleSnapshot(Long roleId, RoleConfigDetailResp roleDetail) {
        JdecMonitorRoleSnapshot snapshot = new JdecMonitorRoleSnapshot();
        snapshot.setRoleId(roleId);
        snapshot.setRoleName(roleDetail.getRoleName());
        snapshot.setSnapshotDate(LocalDate.now());
        snapshot.setCreateTime(LocalDateTime.now());

        roleSnapshotMapper.insert(snapshot);
        log.debug("保存角色快照: roleId={}", roleId);
    }

    private UserDetailResp convertToUserDetailResp(JdecMonitorUserSnapshot snapshot) {
        UserDetailResp resp = new UserDetailResp();
        resp.setUserId(snapshot.getUserId());
        resp.setUserAvatar(snapshot.getUserAvatar());
        resp.setUserName(snapshot.getUserName());
        resp.setWorkNumber(snapshot.getWorkNumber());
        return resp;
    }

    private RoleConfigDetailResp convertToRoleConfigDetailResp(JdecMonitorRoleSnapshot snapshot) {
        RoleConfigDetailResp resp = new RoleConfigDetailResp();
        resp.setId(snapshot.getRoleId());
        resp.setRoleName(snapshot.getRoleName());
        return resp;
    }
}
