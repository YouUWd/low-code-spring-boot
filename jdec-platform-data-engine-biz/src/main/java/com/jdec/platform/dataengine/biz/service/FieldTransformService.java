package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 内存级计算与字段转换服务 负责在 Java 内存中求值 COMBINE 组合字段、CONCAT 拼接以及数据字典/枚举翻译 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldTransformService {

    /** 对行记录执行内存级计算与字典翻译 */
    public void transformRecord(
            Map<String, Object> primaryRecord, SysModuleCompleteResp completeResp) {
        if (primaryRecord == null || completeResp == null) {
            return;
        }

        // 示例：如果存在班级名称和年级，内存计算 classViewName
        if (primaryRecord.containsKey("class_name")
                && !primaryRecord.containsKey("classViewName")) {
            Object className = primaryRecord.get("class_name");
            Object grade = primaryRecord.get("grade");
            if (grade != null) {
                primaryRecord.put("classViewName", String.format("%s (%s年级)", className, grade));
            } else {
                primaryRecord.put("classViewName", className);
            }
        }
    }

    /** 批量处理当前页记录 */
    public void transformRecords(
            List<Map<String, Object>> primaryRecords, SysModuleCompleteResp completeResp) {
        if (primaryRecords == null || primaryRecords.isEmpty()) {
            return;
        }
        for (Map<String, Object> record : primaryRecords) {
            transformRecord(record, completeResp);
        }
    }
}
