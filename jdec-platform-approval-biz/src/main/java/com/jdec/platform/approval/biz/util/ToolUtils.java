package com.jdec.platform.approval.biz.util;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

public class ToolUtils {
    /**
     * 生成业务编号bussinessNumber ， 组织架构和部门管理使用 prefix分别为DM和ORG
     *
     * @param prefix
     * @param cache 是否走缓存生成
     * @param separator 分隔符
     * @param padLength 填充长度
     * @param format 时间格式
     * @return
     */
    public static String createNo(
            String prefix, int cache, String separator, int padLength, String format) {
        String date = "";
        String suffix = "";
        if (StringUtils.isEmpty(format)) {
            date = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        } else {
            date = new SimpleDateFormat(format).format(new Date());
        }
        long total = 0;
        if (cache == 1) {
            String applyTotalKey = date + "hrManage";
            StringRedisTemplate redisTemplate =
                    com.jdec.platform.shared.utils.SpringBeanUtil.getBean(
                            "stringRedisTemplate", StringRedisTemplate.class);
            if (!redisTemplate.hasKey(applyTotalKey)) {
                total = redisTemplate.opsForValue().increment(applyTotalKey);
                long expire = 0;
                if (StringUtils.equals(format, "ymd")) {
                    // 1. 获取明天的日期 (不带时间)
                    LocalDate tomorrow = LocalDate.now().plusDays(1);
                    // 2. 转换为当天的开始时间 (00:00:00)，并指定时区 (通常使用系统默认时区)
                    ZonedDateTime zdt = tomorrow.atStartOfDay(ZoneId.systemDefault());
                    // 3. 获取秒数 (Unix Timestamp)
                    long seconds = zdt.toEpochSecond();
                    expire = seconds - (System.currentTimeMillis() / 1000);
                } else {
                    expire = 1;
                }
                redisTemplate.expire(applyTotalKey, expire, TimeUnit.SECONDS);
            } else {
                total = redisTemplate.opsForValue().increment(applyTotalKey);
            }
            if (StringUtils.isEmpty(separator)) {
                separator = "";
            }
            suffix = date + separator;
            int len = String.valueOf(total).length();
            if (padLength > len) {
                for (int i = 1; i <= padLength - len; i++) {
                    suffix += "0";
                }
            }
            suffix += total;
        } else {
            suffix = date;
        }
        return prefix + suffix;
    }
}
