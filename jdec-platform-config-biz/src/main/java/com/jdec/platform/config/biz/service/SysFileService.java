package com.jdec.platform.config.biz.service;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.IdUtil;
import com.jdec.platform.config.api.SysFileApi;
import com.jdec.platform.config.api.dto.response.SysFileUploadResp;
import com.jdec.platform.shared.config.properties.OssProperties;
import com.jdec.platform.shared.exception.BusinessException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysFileService implements SysFileApi {

    private final S3Client s3Client;
    private final OssProperties ossProperties;

    @Override
    public SysFileUploadResp uploadFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            originalName = "unknown";
        }

        String ext = FileNameUtil.extName(originalName);
        String newFileName = IdUtil.fastSimpleUUID() + (ext.isEmpty() ? "" : "." + ext);

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectKey = datePath + "/" + newFileName;

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

            String url =
                    ossProperties.getEndpoint()
                            + "/"
                            + ossProperties.getBucketName()
                            + "/"
                            + objectKey;

            SysFileUploadResp resp = new SysFileUploadResp();
            resp.setUrl(url);
            resp.setFileName(newFileName);
            resp.setOriginalName(originalName);
            resp.setSize(file.getSize());
            return resp;

        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BusinessException(500, "文件读取失败");
        } catch (Exception e) {
            log.error("OSS 服务异常", e);
            throw new BusinessException(500, "OSS 服务异常: " + e.getMessage());
        }
    }
}
