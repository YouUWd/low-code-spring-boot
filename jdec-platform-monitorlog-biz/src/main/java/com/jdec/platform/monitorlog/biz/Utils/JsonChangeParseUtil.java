package com.jdec.platform.monitorlog.biz.Utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.jdec.platform.monitorlog.api.dto.DataChangeDTO;
import com.jdec.platform.monitorlog.api.dto.FieldChangeDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.Set;

public class JsonChangeParseUtil {
    // 校验器单例
    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * 统一入口：校验JSON并转为指定格式文本
     *
     * @param jsonStr 原始json字符串
     * @return 格式化文本 [ID]:xxx\r\n[字段]:旧->新
     * @throws IllegalArgumentException 格式/业务不合法抛出带描述的异常
     */
    public static String parseAndFormat(String jsonStr) {
        // 1. 空字符串校验
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON字符串不能为空");
        }

        DataChangeDTO dto;
        try {
            // 2. JSON语法校验 + 结构反序列化（语法错误直接抛JSONException）
            dto = JSON.parseObject(jsonStr, DataChangeDTO.class);
        } catch (JSONException e) {
            throw new IllegalArgumentException("JSON语法格式非法：" + e.getMessage(), e);
        }

        // 3. JSR303 业务规则校验（id非空、changeMap至少一个字段）
        Set<ConstraintViolation<DataChangeDTO>> violations = VALIDATOR.validate(dto);
        if (!violations.isEmpty()) {
            StringBuilder errMsg = new StringBuilder("业务校验失败：");
            for (ConstraintViolation<DataChangeDTO> v : violations) {
                errMsg.append(v.getMessage()).append("；");
            }
            throw new IllegalArgumentException(errMsg.toString());
        }

        // 4. 拼接目标格式文本
        StringBuilder sb = new StringBuilder();
        // 主键行
        sb.append("[")
                .append(dto.getKeyName())
                .append("]:")
                .append(dto.getKeyValue())
                .append("\r\n");

        // 遍历所有变更字段
        Map<String, FieldChangeDTO> changeMap = dto.getChangeMap();
        for (Map.Entry<String, FieldChangeDTO> entry : changeMap.entrySet()) {
            String fieldName = entry.getKey();
            FieldChangeDTO field = entry.getValue();
            // 拼接 [字段名]:旧值→新值
            sb.append("[")
                    .append(fieldName)
                    .append("]：")
                    .append(field.getOldVal())
                    .append("→")
                    .append(field.getNewVal())
                    .append("\r\n");
        }

        return sb.toString();
    }
}
