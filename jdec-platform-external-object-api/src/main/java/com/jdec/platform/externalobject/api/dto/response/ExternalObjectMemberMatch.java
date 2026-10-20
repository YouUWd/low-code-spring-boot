package com.jdec.platform.externalobject.api.dto.response;

/**
 * 人员查重命中的已保存人员记录。
 *
 * @param mobilePhoneNo 手机号
 * @param identityNo 证件号
 * @param memberName 人员姓名
 * @param companyName 所属外部对象全称
 */
public record ExternalObjectMemberMatch(
        String mobilePhoneNo, String identityNo, String memberName, String companyName) {}
