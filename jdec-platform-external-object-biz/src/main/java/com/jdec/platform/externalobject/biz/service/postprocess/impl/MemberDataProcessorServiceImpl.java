package com.jdec.platform.externalobject.biz.service.postprocess.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jdec.platform.dataengine.api.dto.model.DataPostParamDTO;
import com.jdec.platform.externalobject.biz.entity.Member;
import com.jdec.platform.externalobject.biz.mapper.MemberMapper;
import com.jdec.platform.externalobject.biz.service.postprocess.CustomizedDataProcessorService;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberDataProcessorServiceImpl implements CustomizedDataProcessorService {

    private final Set<String> ROOT_MODULE_CODES = Set.of("WBDX", "COMPANY-ROOT");
    private final Set<String> MEMBER_MODULE_CODES = Set.of("WBDX", "COMPANY-ROOT");

    private MemberMapper memberMapper;

    @Override
    public void process(DataPostParamDTO dataPostParamDTO) {
        if (dataPostParamDTO.getSaveData() == null) {
            return;
        }
        Set<Long> moduleIds = collectModifyModuleIds(dataPostParamDTO.getSaveData());
        Boolean hasMemberModule =
                moduleIds.stream()
                        .map(x -> dataPostParamDTO.getModuleCacheMap().get(x))
                        .anyMatch(
                                y ->
                                        MEMBER_MODULE_CODES.contains(
                                                y.getModuleInfo().getModuleCode()));
        if (!hasMemberModule) {
            return;
        }
        LambdaQueryWrapper<Member> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Member::getCompanyId, dataPostParamDTO.getRootDataId());
        List<Member> members = memberMapper.selectList(queryWrapper);
        if (CollectionUtil.isEmpty(members)) {
            return;
        }

        List<Member> deptList = new ArrayList<>();
        Map<String, Member> deleteMap = new HashMap<>();
        Map<String, Member> personMap = new HashMap<>();
        for (Member member : members) {
            if (Integer.valueOf(1).equals(member.getDeleted())) {
                deleteMap.put(member.getNodeId(), member);
            } else {

                if ("部门".equals(member.getType())) {
                    deptList.add(member);
                } else {
                    personMap.put(member.getNodeId(), member);
                }
            }
        }

        if (CollectionUtil.isEmpty(deptList)) {
            return;
        }

        //        {
        //            "name": "王一",
        //                "phone": "18562542542",
        //                "avatar": null,
        //                "gender": null,
        //                "nodeId": "e418abaf-9152-4048-9e58-c9b3236862e8",
        //                "jobName": "市场部",
        //                "disabled": false,
        //                "position": null,
        //                "jobNumber": ""
        //        }

        List<LambdaUpdateWrapper<Member>> updateWrapperList = new ArrayList<>();

        for (Member dept : deptList) {
            Map object = (Map) dept.getDeptLeader();
            if (object == null || CollectionUtil.isEmpty(object)) {
                continue;
            }
            String nodeId = (String) object.get("nodeId");

            if (deleteMap.containsKey(nodeId)) {

                // 已经删除了
                LambdaUpdateWrapper<Member> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(Member::getId, dept.getId());
                updateWrapper.set(Member::getDeptLeader, null);
                updateWrapper.set(Member::getUpdatedDate, LocalDateTime.now());
                updateWrapper.set(
                        Member::getUpdatedBy, dataPostParamDTO.getLoginUser().getUserId());
                updateWrapperList.add(updateWrapper);
            } else if (personMap.containsKey(nodeId)) {
                // 跟新

                Member person = personMap.get(nodeId);

                //                object.put("name", person.getName());
                //                object.put("phone", person.getMobilePhoneNo());
                //                object.put("avatar", person.getAvatar());
                //                object.put("gender", person.getGender());
                //                object.put("nodeId", person.getNodeId());
                //                object.put("jobName", person.getJobName());
                //                object.put("disabled", person.getDisabled());
                //                object.put("position", person.getPosition());
                //                object.put("jobNumber", person.getJobNumber());

                LambdaUpdateWrapper<Member> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(Member::getId, dept.getId());
                updateWrapper.set(Member::getDeptLeader, null);
                updateWrapper.set(Member::getUpdatedDate, LocalDateTime.now());
                updateWrapper.set(
                        Member::getUpdatedBy, dataPostParamDTO.getLoginUser().getUserId());
                updateWrapperList.add(updateWrapper);

            } else {
                // 未知情况
            }
        }
        if (CollectionUtil.isNotEmpty(updateWrapperList)) {
            updateWrapperList.stream().forEach(x -> memberMapper.update(x));
        }
    }

    @Override
    public Boolean isApplicable(DataPostParamDTO dataPostParamDTO) {
        return ROOT_MODULE_CODES.contains(dataPostParamDTO.getRootModuleCode());
    }
}
