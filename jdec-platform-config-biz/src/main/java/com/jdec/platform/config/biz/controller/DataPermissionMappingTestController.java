package com.jdec.platform.config.biz.controller;

import cn.hutool.json.JSONUtil;
import com.jdec.platform.config.biz.dto.DataPermissionMappingMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

/** 数据权限映射测试控制器（用于测试 Redis 消息发布） */
@RestController
@RequestMapping("/test/data-permission-mapping")
@RequiredArgsConstructor
@Tag(name = "数据权限映射测试", description = "用于测试 Redis 消息发布")
public class DataPermissionMappingTestController {

    private final RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/publish")
    @Operation(summary = "发布数据权限映射消息")
    public String publishMessage(@RequestBody DataPermissionMappingMessage message) {
        String channel = "data-permission-mapping";
        String jsonMessage = JSONUtil.toJsonStr(message);
        redisTemplate.convertAndSend(channel, jsonMessage);
        return "消息发布成功: " + jsonMessage;
    }

    @GetMapping("/example")
    @Operation(summary = "获取示例消息")
    public DataPermissionMappingMessage getExampleMessage() {
        DataPermissionMappingMessage message = new DataPermissionMappingMessage();
        message.setDataCode("PROJECT_DATA");
        message.setSubjectId(1L);
        message.setProjectNo("PRJ001");
        message.setOperate("add");

        List<DataPermissionMappingMessage.DataItem> dataList = new ArrayList<>();
        DataPermissionMappingMessage.DataItem item1 = new DataPermissionMappingMessage.DataItem();
        item1.setBizId(100L);
        item1.setBizName("业务数据1");
        dataList.add(item1);

        DataPermissionMappingMessage.DataItem item2 = new DataPermissionMappingMessage.DataItem();
        item2.setBizId(101L);
        item2.setBizName("业务数据2");
        dataList.add(item2);

        message.setDataList(dataList);

        return message;
    }
}
