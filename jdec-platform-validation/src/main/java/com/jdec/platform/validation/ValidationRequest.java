package com.jdec.platform.validation;

/**
 * 业务校验入参。
 *
 * @param moduleId 模块标识（请求携带的 {@code nestedModuleData.moduleId}）
 * @param projectNo 项目编码
 * @param subjectId 主体ID
 * @param payload 前端请求体（DTO / Map / JsonNode 均可）
 */
public record ValidationRequest(Long moduleId, String projectNo, Long subjectId, Object payload) {}
