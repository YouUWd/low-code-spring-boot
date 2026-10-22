package com.jdec.platform.common.biz.service.impl;

import cn.hutool.core.io.file.FileNameUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.common.api.dto.response.FileSimpleResp;
import com.jdec.platform.common.api.dto.response.StatusSimpleResp;
import com.jdec.platform.common.api.dto.response.UserSimpleResp;
import com.jdec.platform.common.biz.service.QueryResultEnhanceService;
import com.jdec.platform.config.api.SysFileApi;
import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.dto.request.QueryUsersByIdsReq;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.config.api.dto.response.ModuleApprovalChainConfigResp;
import com.jdec.platform.config.api.dto.response.SysFileUploadResp;
import com.jdec.platform.config.api.dto.response.SysUserPageResp;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/** 菜单查询结果与候选项数据增强服务实现类 */
@Slf4j
@Service
public class QueryResultEnhanceServiceImpl implements QueryResultEnhanceService {

    private final SysUserApi sysUserApi;
    private final SysStatusApi sysStatusApi;
    private final SysFileApi sysFileApi;
    private final ObjectMapper objectMapper;

    @Autowired
    public QueryResultEnhanceServiceImpl(
            @Autowired(required = false) SysUserApi sysUserApi,
            @Autowired(required = false) SysStatusApi sysStatusApi,
            @Autowired(required = false) SysFileApi sysFileApi) {
        this.sysUserApi = sysUserApi;
        this.sysStatusApi = sysStatusApi;
        this.sysFileApi = sysFileApi;
        this.objectMapper = new ObjectMapper();
    }

    public QueryResultEnhanceServiceImpl(
            SysUserApi sysUserApi,
            SysStatusApi sysStatusApi,
            SysFileApi sysFileApi,
            ObjectMapper objectMapper) {
        this.sysUserApi = sysUserApi;
        this.sysStatusApi = sysStatusApi;
        this.sysFileApi = sysFileApi;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public void enhanceDataRows(List<DataRow> records, Long rootModuleId, String primaryTable) {
        enhanceDataRows(records, rootModuleId, primaryTable, null);
    }

    @Override
    public void enhanceDataRows(
            List<DataRow> records,
            Long rootModuleId,
            String primaryTable,
            MenuHeaderColumnResp headerRoot) {
        if (CollectionUtils.isEmpty(records)) {
            return;
        }

        Map<String, String> fieldBusinessTypeMap = extractFieldBusinessTypeMap(headerRoot);

        Set<Long> userIds = new HashSet<>();
        Set<Integer> statusValues = new HashSet<>();
        Set<Long> fileIds = new HashSet<>();

        // 阶段 1: 扫描所有数据行收集 userIds, statusValues, fileIds
        for (DataRow row : records) {
            if (row == null) {
                continue;
            }
            if (primaryTable != null && !primaryTable.isBlank()) {
                Long uid = row.getLong(primaryTable, "created_by");
                if (uid != null) {
                    userIds.add(uid);
                }
                Integer sv = row.getInteger(primaryTable, "business_status");
                if (sv != null) {
                    statusValues.add(sv);
                }
            }

            Map<String, Object> tables = row.getTables();
            if (tables != null) {
                for (Map.Entry<String, Object> tableEntry : tables.entrySet()) {
                    String tbl = tableEntry.getKey();
                    if (tableEntry.getValue() instanceof Map<?, ?> colMap) {
                        for (Map.Entry<?, ?> colEntry : colMap.entrySet()) {
                            if (colEntry.getKey() instanceof String colName) {
                                String fullKey = (tbl + "." + colName).toLowerCase();
                                String bType = fieldBusinessTypeMap.get(fullKey);
                                if (bType == null) {
                                    bType = fieldBusinessTypeMap.get(colName.toLowerCase());
                                }
                                if (isFileType(bType)) {
                                    collectFileIdsFromValue(colEntry.getValue(), fileIds);
                                }
                            }
                        }
                    }
                }
            }
        }

        Map<Long, UserSimpleResp> userMap = queryUserMap(userIds);
        Map<Integer, StatusSimpleResp> statusMap = queryStatusMap(statusValues, rootModuleId);
        Map<Long, FileSimpleResp> fileMap = queryFileMap(fileIds);

        // 阶段 2: 填充结构化对象
        for (DataRow row : records) {
            if (row == null) {
                continue;
            }
            if (primaryTable != null && !primaryTable.isBlank()) {
                Long uid = row.getLong(primaryTable, "created_by");
                if (uid != null) {
                    UserSimpleResp userResp = userMap.get(uid);
                    row.putValue(
                            primaryTable,
                            "created_by",
                            userResp != null
                                    ? userResp
                                    : UserSimpleResp.builder().userId(uid).build());
                }
                Integer sv = row.getInteger(primaryTable, "business_status");
                if (sv != null) {
                    StatusSimpleResp statusResp = statusMap.get(sv);
                    row.putValue(
                            primaryTable,
                            "business_status",
                            statusResp != null
                                    ? statusResp
                                    : StatusSimpleResp.builder().statusValue(sv).build());
                }
            }

            Map<String, Object> tables = row.getTables();
            if (tables != null) {
                for (Map.Entry<String, Object> tableEntry : tables.entrySet()) {
                    String tbl = tableEntry.getKey();
                    if (tableEntry.getValue() instanceof Map<?, ?> colMap) {
                        for (Map.Entry<?, ?> colEntry : colMap.entrySet()) {
                            if (colEntry.getKey() instanceof String colName) {
                                String fullKey = (tbl + "." + colName).toLowerCase();
                                String bType = fieldBusinessTypeMap.get(fullKey);
                                if (bType == null) {
                                    bType = fieldBusinessTypeMap.get(colName.toLowerCase());
                                }
                                if (isFileType(bType)) {
                                    Object rawVal = colEntry.getValue();
                                    if (rawVal != null) {
                                        Object converted = convertToFileObject(rawVal, fileMap);
                                        row.putValue(tbl, colName, converted);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 递归处理自相似子区块
            if (row.getChildren() != null) {
                for (DataBlock childBlock : row.getChildren()) {
                    if (childBlock != null && !CollectionUtils.isEmpty(childBlock.getRecords())) {
                        enhanceDataRows(
                                childBlock.getRecords(),
                                childBlock.getModuleId(),
                                null,
                                headerRoot);
                    }
                }
            }
        }
    }

    @Override
    public List<DataOptionResp> enhanceOptions(
            List<DataOptionResp> rawOptions, String columnName, Long rootModuleId) {
        return enhanceOptions(rawOptions, columnName, rootModuleId, (String) null);
    }

    @Override
    public List<DataOptionResp> enhanceOptions(
            List<DataOptionResp> rawOptions,
            String columnName,
            Long rootModuleId,
            String businessTypeCode) {
        if (CollectionUtils.isEmpty(rawOptions) || columnName == null || columnName.isBlank()) {
            return rawOptions != null ? rawOptions : Collections.emptyList();
        }

        String col = columnName.trim();
        if ("created_by".equalsIgnoreCase(col)) {
            Set<Long> userIds = new LinkedHashSet<>();
            for (DataOptionResp opt : rawOptions) {
                if (opt != null && opt.getValue() instanceof Number n) {
                    userIds.add(n.longValue());
                }
            }
            Map<Long, UserSimpleResp> userMap = queryUserMap(userIds);
            for (DataOptionResp opt : rawOptions) {
                if (opt != null && opt.getValue() instanceof Number n) {
                    Long uid = n.longValue();
                    UserSimpleResp finalUser =
                            userMap.getOrDefault(uid, UserSimpleResp.builder().userId(uid).build());
                    opt.setValue(finalUser);
                    opt.setLabel(finalUser);
                }
            }
            return rawOptions;
        }

        if ("business_status".equalsIgnoreCase(col)) {
            Set<Integer> statusValues = new LinkedHashSet<>();
            for (DataOptionResp opt : rawOptions) {
                if (opt != null && opt.getValue() instanceof Number n) {
                    statusValues.add(n.intValue());
                }
            }
            Map<Integer, StatusSimpleResp> statusMap = queryStatusMap(statusValues, rootModuleId);
            for (DataOptionResp opt : rawOptions) {
                if (opt != null && opt.getValue() instanceof Number n) {
                    Integer sv = n.intValue();
                    StatusSimpleResp finalStatus =
                            statusMap.getOrDefault(
                                    sv, StatusSimpleResp.builder().statusValue(sv).build());
                    opt.setValue(finalStatus);
                    opt.setLabel(finalStatus);
                }
            }
            return rawOptions;
        }

        if (isFileType(businessTypeCode)) {
            Set<Long> fileIds = new LinkedHashSet<>();
            for (DataOptionResp opt : rawOptions) {
                if (opt != null && opt.getValue() != null) {
                    collectFileIdsFromValue(opt.getValue(), fileIds);
                }
            }
            Map<Long, FileSimpleResp> fileMap = queryFileMap(fileIds);
            for (DataOptionResp opt : rawOptions) {
                if (opt != null && opt.getValue() != null) {
                    Object converted = convertToFileObject(opt.getValue(), fileMap);
                    if (converted instanceof FileSimpleResp f) {
                        opt.setValue(f.getId() != null ? f.getId() : opt.getValue());
                        String label = formatFileLabel(f);
                        opt.setLabel(label != null ? label : opt.getLabel());
                    } else if (converted instanceof List<?> list) {
                        List<Long> ids = new ArrayList<>();
                        List<String> names = new ArrayList<>();
                        for (Object item : list) {
                            if (item instanceof FileSimpleResp itemFile) {
                                if (itemFile.getId() != null) {
                                    ids.add(itemFile.getId());
                                }
                                String l = formatFileLabel(itemFile);
                                if (StringUtils.hasText(l)) {
                                    names.add(l);
                                }
                            }
                        }
                        if (!ids.isEmpty()) {
                            opt.setValue(ids.size() == 1 ? ids.get(0) : ids);
                        }
                        if (!names.isEmpty()) {
                            opt.setLabel(String.join(", ", names));
                        }
                    }
                }
            }
            return rawOptions;
        }

        return rawOptions;
    }

    @Override
    public List<DataOptionResp> enhanceOptions(
            List<DataOptionResp> rawOptions,
            String columnName,
            Long rootModuleId,
            MenuHeaderColumnResp headerRoot,
            Long fieldId) {
        String businessTypeCode = findBusinessTypeCodeInHeader(headerRoot, fieldId, columnName);
        return enhanceOptions(rawOptions, columnName, rootModuleId, businessTypeCode);
    }

    private Map<String, String> extractFieldBusinessTypeMap(MenuHeaderColumnResp node) {
        Map<String, String> map = new HashMap<>();
        if (node == null) {
            return map;
        }
        collectFieldBusinessType(node, map);
        return map;
    }

    private void collectFieldBusinessType(MenuHeaderColumnResp node, Map<String, String> map) {
        if (node == null) {
            return;
        }
        if (StringUtils.hasText(node.getBusinessTypeCode())) {
            String bType = node.getBusinessTypeCode().trim();
            if (StringUtils.hasText(node.getDataIndex())) {
                String di = node.getDataIndex().trim();
                map.put(di.toLowerCase(), bType);
                if (di.contains(".")) {
                    String cleanCol = di.substring(di.lastIndexOf('.') + 1);
                    map.put(cleanCol.toLowerCase(), bType);
                    String[] parts = di.split("\\.");
                    if (parts.length >= 2) {
                        String table = parts[parts.length - 2];
                        map.put((table + "." + cleanCol).toLowerCase(), bType);
                    }
                }
            }
        }
        if (node.getChildren() != null) {
            for (MenuHeaderColumnResp child : node.getChildren()) {
                collectFieldBusinessType(child, map);
            }
        }
    }

    private String findBusinessTypeCodeInHeader(
            MenuHeaderColumnResp node, Long fieldId, String columnName) {
        if (node == null) {
            return null;
        }
        if (fieldId != null && fieldId.equals(node.getFieldId())) {
            return node.getBusinessTypeCode();
        }
        if (StringUtils.hasText(columnName) && StringUtils.hasText(node.getDataIndex())) {
            String di = node.getDataIndex().trim().toLowerCase();
            String col = columnName.trim().toLowerCase();
            if (di.equals(col) || di.endsWith("." + col)) {
                return node.getBusinessTypeCode();
            }
        }
        if (node.getChildren() != null) {
            for (MenuHeaderColumnResp child : node.getChildren()) {
                String code = findBusinessTypeCodeInHeader(child, fieldId, columnName);
                if (code != null) {
                    return code;
                }
            }
        }
        return null;
    }

    private static boolean isFileType(String businessTypeCode) {
        if (!StringUtils.hasText(businessTypeCode)) {
            return false;
        }
        String code = businessTypeCode.trim();
        return "image".equalsIgnoreCase(code)
                || "imageEnum".equalsIgnoreCase(code)
                || "attachment".equalsIgnoreCase(code)
                || "file".equalsIgnoreCase(code);
    }

    private Map<Long, FileSimpleResp> queryFileMap(Set<Long> fileIds) {
        Map<Long, FileSimpleResp> fileMap = new HashMap<>();
        if (CollectionUtils.isEmpty(fileIds)) {
            return fileMap;
        }
        if (sysFileApi != null) {
            try {
                List<SysFileUploadResp> fileList =
                        sysFileApi.listFilesByIds(new ArrayList<>(fileIds));
                if (fileList != null) {
                    for (SysFileUploadResp f : fileList) {
                        if (f == null || f.getId() == null) {
                            continue;
                        }
                        fileMap.put(
                                f.getId(),
                                FileSimpleResp.builder()
                                        .id(f.getId())
                                        .name(
                                                f.getOriginalName() != null
                                                        ? f.getOriginalName()
                                                        : f.getFileName())
                                        .fileExt(f.getFileExt())
                                        .url(f.getUrl())
                                        .build());
                    }
                }
            } catch (Exception ex) {
                log.warn("批量查询文件信息失败: fileIds={}, err={}", fileIds, ex.getMessage());
            }
        }
        return fileMap;
    }

    private void collectFileIdsFromValue(Object val, Set<Long> fileIds) {
        if (val == null) {
            return;
        }
        if (val instanceof Number n) {
            fileIds.add(n.longValue());
            return;
        }
        if (val instanceof Collection<?> coll) {
            for (Object item : coll) {
                collectFileIdsFromValue(item, fileIds);
            }
            return;
        }
        if (val instanceof Map<?, ?> map) {
            Object idObj = map.get("id");
            if (idObj != null) {
                collectFileIdsFromValue(idObj, fileIds);
            }
            return;
        }
        if (val instanceof String s) {
            String str = s.trim();
            if (str.isEmpty()) {
                return;
            }
            if (str.startsWith("[") && str.endsWith("]")) {
                try {
                    List<?> list = objectMapper.readValue(str, List.class);
                    for (Object item : list) {
                        collectFileIdsFromValue(item, fileIds);
                    }
                    return;
                } catch (Exception ignored) {
                }
            }
            if (str.startsWith("{") && str.endsWith("}")) {
                try {
                    Map<?, ?> map = objectMapper.readValue(str, Map.class);
                    collectFileIdsFromValue(map, fileIds);
                    return;
                } catch (Exception ignored) {
                }
            }
            if (str.contains(",")) {
                String[] parts = str.split(",");
                for (String p : parts) {
                    collectFileIdsFromValue(p.trim(), fileIds);
                }
                return;
            }
            if (str.chars().allMatch(Character::isDigit)) {
                try {
                    fileIds.add(Long.parseLong(str));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private Object convertToFileObject(Object val, Map<Long, FileSimpleResp> fileMap) {
        if (val == null) {
            return null;
        }
        if (val instanceof FileSimpleResp) {
            return val;
        }
        if (val instanceof Number n) {
            Long fid = n.longValue();
            return fileMap.getOrDefault(fid, FileSimpleResp.builder().id(fid).build());
        }
        if (val instanceof Collection<?> coll) {
            List<FileSimpleResp> list = new ArrayList<>();
            for (Object item : coll) {
                FileSimpleResp f = convertSingleToFileObject(item, fileMap);
                if (f != null) {
                    list.add(f);
                }
            }
            return list;
        }
        if (val instanceof Map<?, ?> map) {
            return convertMapToFileObject(map, fileMap);
        }
        if (val instanceof String s) {
            String str = s.trim();
            if (str.isEmpty()) {
                return null;
            }
            if (str.startsWith("[") && str.endsWith("]")) {
                try {
                    List<?> rawList = objectMapper.readValue(str, List.class);
                    List<FileSimpleResp> list = new ArrayList<>();
                    for (Object item : rawList) {
                        FileSimpleResp f = convertSingleToFileObject(item, fileMap);
                        if (f != null) {
                            list.add(f);
                        }
                    }
                    return list;
                } catch (Exception ignored) {
                }
            }
            if (str.startsWith("{") && str.endsWith("}")) {
                try {
                    Map<?, ?> map = objectMapper.readValue(str, Map.class);
                    return convertMapToFileObject(map, fileMap);
                } catch (Exception ignored) {
                }
            }
            if (str.contains(",")) {
                String[] parts = str.split(",");
                List<FileSimpleResp> list = new ArrayList<>();
                for (String p : parts) {
                    FileSimpleResp f = convertSingleToFileObject(p.trim(), fileMap);
                    if (f != null) {
                        list.add(f);
                    }
                }
                return list;
            }
            return convertSingleToFileObject(str, fileMap);
        }
        return val;
    }

    private FileSimpleResp convertSingleToFileObject(
            Object item, Map<Long, FileSimpleResp> fileMap) {
        if (item == null) {
            return null;
        }
        if (item instanceof FileSimpleResp f) {
            return f;
        }
        if (item instanceof Number n) {
            Long fid = n.longValue();
            return fileMap.getOrDefault(fid, FileSimpleResp.builder().id(fid).build());
        }
        if (item instanceof Map<?, ?> map) {
            return convertMapToFileObject(map, fileMap);
        }
        if (item instanceof String s) {
            String str = s.trim();
            if (str.isEmpty()) {
                return null;
            }
            if (str.chars().allMatch(Character::isDigit)) {
                try {
                    Long fid = Long.parseLong(str);
                    return fileMap.getOrDefault(fid, FileSimpleResp.builder().id(fid).build());
                } catch (NumberFormatException ignored) {
                }
            }
            String cleanUrl = str.contains("?") ? str.substring(0, str.indexOf('?')) : str;
            String baseName = FileNameUtil.mainName(cleanUrl);
            String ext = FileNameUtil.extName(cleanUrl);
            return FileSimpleResp.builder().name(baseName).fileExt(ext).url(str).build();
        }
        return null;
    }

    private FileSimpleResp convertMapToFileObject(
            Map<?, ?> map, Map<Long, FileSimpleResp> fileMap) {
        Long id = null;
        Object idVal = map.get("id");
        if (idVal instanceof Number n) {
            id = n.longValue();
        } else if (idVal instanceof String s && s.chars().allMatch(Character::isDigit)) {
            try {
                id = Long.parseLong(s);
            } catch (NumberFormatException ignored) {
            }
        }
        if (id != null && fileMap.containsKey(id)) {
            return fileMap.get(id);
        }

        String url = map.get("url") != null ? String.valueOf(map.get("url")) : null;
        String origName =
                map.get("originalName") != null ? String.valueOf(map.get("originalName")) : null;
        String name = map.get("name") != null ? String.valueOf(map.get("name")) : origName;
        String cleanUrl =
                (url != null && url.contains("?")) ? url.substring(0, url.indexOf('?')) : url;
        String ext =
                map.get("fileExt") != null
                        ? String.valueOf(map.get("fileExt"))
                        : (cleanUrl != null ? FileNameUtil.extName(cleanUrl) : null);
        if (name == null && cleanUrl != null) {
            name = FileNameUtil.mainName(cleanUrl);
        }

        return FileSimpleResp.builder().id(id).name(name).fileExt(ext).url(url).build();
    }

    private Map<Long, UserSimpleResp> queryUserMap(Set<Long> userIds) {
        Map<Long, UserSimpleResp> userMap = new HashMap<>();
        if (CollectionUtils.isEmpty(userIds)) {
            return userMap;
        }
        try {
            QueryUsersByIdsReq queryReq = new QueryUsersByIdsReq();
            queryReq.setUserIds(new ArrayList<>(userIds));
            List<SysUserPageResp> userList = sysUserApi.getUsersByUserIds(queryReq);
            if (userList != null) {
                for (SysUserPageResp u : userList) {
                    if (u == null) {
                        continue;
                    }
                    UserSimpleResp resp =
                            UserSimpleResp.builder()
                                    .userId(u.getUserId())
                                    .userName(u.getUserName())
                                    .userAvatar(u.getUserAvatar())
                                    .workNumber(u.getWorkNumber())
                                    .build();
                    if (u.getUserId() != null) {
                        userMap.put(u.getUserId(), resp);
                    }
                    if (u.getId() != null) {
                        userMap.putIfAbsent(u.getId(), resp);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("批量查询用户信息失败: userIds={}, err={}", userIds, ex.getMessage());
        }
        return userMap;
    }

    private Map<Integer, StatusSimpleResp> queryStatusMap(
            Set<Integer> statusValues, Long rootModuleId) {
        Map<Integer, StatusSimpleResp> statusMap = new HashMap<>();
        if (CollectionUtils.isEmpty(statusValues) || rootModuleId == null) {
            return statusMap;
        }
        for (Integer sv : statusValues) {
            try {
                ModuleApprovalChainConfigResp.StatusNode node =
                        sysStatusApi.getStatusByValue(sv, rootModuleId);
                if (node != null) {
                    statusMap.put(
                            sv,
                            StatusSimpleResp.builder()
                                    .title(node.getTitle())
                                    .statusValue(node.getStatusValue())
                                    .statusBackground(node.getStatusBackground())
                                    .statusFontColor(node.getStatusFontColor())
                                    .build());
                }
            } catch (Exception ex) {
                log.warn(
                        "获取模块状态信息失败: moduleId={}, statusValue={}, err={}",
                        rootModuleId,
                        sv,
                        ex.getMessage());
            }
        }
        return statusMap;
    }

    private String formatFileLabel(FileSimpleResp f) {
        if (f == null) {
            return null;
        }
        String name = f.getName();
        String ext = f.getFileExt();
        if (StringUtils.hasText(name)) {
            if (StringUtils.hasText(ext) && !name.toLowerCase().endsWith("." + ext.toLowerCase())) {
                return name + "." + ext;
            }
            return name;
        }
        if (f.getId() != null) {
            return String.valueOf(f.getId());
        }
        if (StringUtils.hasText(f.getUrl())) {
            return FileNameUtil.getName(f.getUrl());
        }
        return null;
    }
}
