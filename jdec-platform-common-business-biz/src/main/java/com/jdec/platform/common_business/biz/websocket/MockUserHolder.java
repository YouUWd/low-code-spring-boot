package com.jdec.platform.common_business.biz.websocket;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ConcurrentHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MockUserHolder {

    public static final ConcurrentHashMap<Long, ConcurrentHashSet<Long>> MOCK_USER_TO_LOGIN_USER =
            new ConcurrentHashMap();

    public static void addMapping(Long mockUserId, Long loginUserId) {
        if (mockUserId == null || loginUserId == null) {
            return;
        }
        if (!MOCK_USER_TO_LOGIN_USER.contains(mockUserId)) {
            MOCK_USER_TO_LOGIN_USER.putIfAbsent(mockUserId, new ConcurrentHashSet<>());
        }
        MOCK_USER_TO_LOGIN_USER.get(mockUserId).add(loginUserId);
    }

    public static void removeMapping(Long mockUserId, Long loginUserId) {
        if (MOCK_USER_TO_LOGIN_USER.contains(mockUserId)) {
            synchronized (MockUserHolder.class) {
                if (MOCK_USER_TO_LOGIN_USER.contains(mockUserId)) {
                    MOCK_USER_TO_LOGIN_USER.get(mockUserId).remove(loginUserId);
                    if (MOCK_USER_TO_LOGIN_USER.get(mockUserId).size() == 0) {
                        MOCK_USER_TO_LOGIN_USER.remove(mockUserId);
                    }
                }
            }
        }
    }

    public static Boolean isCurrentUserMocked(Long userId) {
        return MOCK_USER_TO_LOGIN_USER.contains(userId);
    }

    public static Set<Long> getMappingList(Long userId) {
        Set<Long> userIds = MOCK_USER_TO_LOGIN_USER.get(userId);
        if (CollectionUtil.isNotEmpty(userIds)) {
            return userIds.stream().collect(Collectors.toSet());
        }
        return Set.of();
    }
}
