package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.response.SysFileUploadResp;
import org.springframework.web.multipart.MultipartFile;

public interface SysFileApi {

    SysFileUploadResp uploadFile(MultipartFile file);
}
