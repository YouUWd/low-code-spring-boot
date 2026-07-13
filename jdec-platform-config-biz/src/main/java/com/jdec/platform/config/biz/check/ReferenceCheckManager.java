package com.jdec.platform.config.biz.check;

import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ReferenceCheckManager {
    @Resource private List<ReferenceChecker> checkers;

    public List<String> check(ReferenceContext context) {
        return checkers.stream()
                .filter(checker -> checker.targetType().contains(context.getTargetType()))
                .map(checker -> checker.check(context))
                .filter(ReferenceCheckResult::isReferenced)
                .map(ReferenceCheckResult::getMessage)
                .toList();
    }
}
