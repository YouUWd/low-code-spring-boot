package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.response.SysFileUploadResp;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface SysFileApi {

    /**
     * 上传文件并关联菜单
     *
     * @param file 文件流
     * @param menuId 菜单ID (可选)
     * @return 上传结果响应
     */
    SysFileUploadResp uploadFile(MultipartFile file, Long menuId);

    /**
     * 上传文件 (通用上传，不指定菜单)
     *
     * @param file 文件流
     * @return 上传结果响应
     */
    default SysFileUploadResp uploadFile(MultipartFile file) {
        return uploadFile(file, null);
    }

    /**
     * 根据主键ID批量查询文件记录
     *
     * @param ids 文件主键ID列表
     * @return 文件响应列表
     */
    List<SysFileUploadResp> listFilesByIds(List<Long> ids);

    /**
     * 根据主键ID查询单个文件记录
     *
     * @param id 文件主键ID
     * @return 文件响应（不存在返回 null）
     */
    default SysFileUploadResp getFileById(Long id) {
        if (id == null) {
            return null;
        }
        List<SysFileUploadResp> list = listFilesByIds(List.of(id));
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    /**
     * 获取文件访问路径前缀 (Base URL / Prefix)
     *
     * @return 文件访问路径前缀 (如 http://192.168.60.8:9001/jdec-platform/)
     */
    String getPathPrefix();
}
