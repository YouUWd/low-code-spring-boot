package com.jdec.platform.config.biz.service;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.IdUtil;
import com.jdec.platform.config.api.SysFileApi;
import com.jdec.platform.config.api.dto.response.SysFileUploadResp;
import com.jdec.platform.config.biz.entity.BusinessFile;
import com.jdec.platform.config.biz.mapper.BusinessFileMapper;
import com.jdec.platform.shared.config.properties.OssProperties;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysFileService implements SysFileApi {

    private final S3Client s3Client;
    private final OssProperties ossProperties;
    private final BusinessFileMapper businessFileMapper;

    @Override
    public SysFileUploadResp uploadFile(MultipartFile file, Long menuId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "unknown";
        }

        String baseName = FileNameUtil.mainName(originalFilename);
        String ext = FileNameUtil.extName(originalFilename);
        String newFileName = IdUtil.fastSimpleUUID() + (ext.isEmpty() ? "" : "." + ext);

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectKey = datePath + "/" + newFileName;

        // 1. 上传文件流至对象存储
        try {
            PutObjectRequest putObjectRequest =
                    PutObjectRequest.builder()
                            .bucket(ossProperties.getBucketName())
                            .key(objectKey)
                            .contentType(file.getContentType())
                            .build();

            s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            log.error("文件读取失败: originalName={}, err={}", originalFilename, e.getMessage(), e);
            throw new BusinessException(500, "文件读取失败");
        } catch (Exception e) {
            log.error("OSS 服务异常: originalName={}, err={}", originalFilename, e.getMessage(), e);
            throw new BusinessException(500, "OSS 服务异常: " + e.getMessage());
        }

        // 2. 构造记录并持久化到业务库的 business_file 表 (由 ProjectAwareRoutingDataSource 依据
        // projectNo 自动路由)
        BusinessFile businessFile =
                BusinessFile.builder()
                        .projectNo(AppContext.getProjectNo())
                        .subjectId(
                                AppContext.getSubjectId() != null ? AppContext.getSubjectId() : 0L)
                        .menuId(menuId)
                        .fileName(newFileName)
                        .originalName(baseName)
                        .fileExt(ext)
                        .fileSize(file.getSize())
                        .contentType(file.getContentType())
                        .bucketName(ossProperties.getBucketName())
                        .objectKey(objectKey)
                        .createdBy(UserContext.getUserId())
                        .createdDate(LocalDateTime.now())
                        .deleted(0)
                        .build();

        try {
            businessFileMapper.insert(businessFile);
        } catch (Exception ex) {
            log.error(
                    "保存文件上传记录到业务数据库失败: projectNo={}, bucket={}, objectKey={}, err={}",
                    AppContext.getProjectNo(),
                    ossProperties.getBucketName(),
                    objectKey,
                    ex.getMessage(),
                    ex);
            try {
                s3Client.deleteObject(
                        DeleteObjectRequest.builder()
                                .bucket(ossProperties.getBucketName())
                                .key(objectKey)
                                .build());
            } catch (Exception deleteEx) {
                log.warn(
                        "补偿删除 OSS 文件失败: bucket={}, key={}, err={}",
                        ossProperties.getBucketName(),
                        objectKey,
                        deleteEx.getMessage());
            }
            throw new BusinessException(500, "保存文件记录失败");
        }

        return SysFileUploadResp.builder()
                .id(businessFile.getId())
                .name(baseName)
                .fileExt(ext)
                .objectKey(objectKey)
                .build();
    }

    @Override
    public List<SysFileUploadResp> listFilesByIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }
        // 由 ProjectAwareRoutingDataSource 依据 AppContext.getProjectNo() 自动路由到对应业务库
        List<BusinessFile> files = businessFileMapper.selectByIds(ids);
        if (CollectionUtils.isEmpty(files)) {
            return Collections.emptyList();
        }
        return files.stream()
                .filter(Objects::nonNull)
                .map(this::toUploadResp)
                .collect(Collectors.toList());
    }

    @Override
    public String getPathPrefix() {
        if (ossProperties == null) {
            return "";
        }
        if (StringUtils.hasText(ossProperties.getDomain())) {
            String domain = ossProperties.getDomain().trim();
            return domain.endsWith("/") ? domain : domain + "/";
        }
        String endpoint = ossProperties.getEndpoint();
        if (!StringUtils.hasText(endpoint)) {
            return "";
        }
        StringBuilder prefix = new StringBuilder(endpoint.trim());
        if (prefix.charAt(prefix.length() - 1) == '/') {
            prefix.deleteCharAt(prefix.length() - 1);
        }
        if (StringUtils.hasText(ossProperties.getBucketName())) {
            prefix.append("/").append(ossProperties.getBucketName().trim());
        }
        return prefix.append("/").toString();
    }

    private SysFileUploadResp toUploadResp(BusinessFile businessFile) {
        if (businessFile == null) {
            return null;
        }
        return SysFileUploadResp.builder()
                .id(businessFile.getId())
                .name(businessFile.getOriginalName())
                .fileExt(businessFile.getFileExt())
                .objectKey(businessFile.getObjectKey())
                .build();
    }
}
