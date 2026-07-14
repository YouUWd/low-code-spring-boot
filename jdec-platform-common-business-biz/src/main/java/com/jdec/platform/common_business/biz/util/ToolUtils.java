package com.jdec.platform.common_business.biz.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ModuleNodeResp;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** ToolUtils工具类，实现日期转换，返回数据格式化等公共方法。 */
public class ToolUtils {

    // 默认日期格式化类
    private static final DateTimeFormatter DEFAULT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 默认时间格式化类
    private static final DateTimeFormatter DEFAULT_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 默认时刻格式化类
    private static final DateTimeFormatter DEFAULT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolUtils.class);

    public static String schemeAndHttpHost() {
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                        .getRequest();
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int port = request.getServerPort();
        return scheme + "://" + serverName + (port != 80 && port != 443 ? ":" + port : "");
    }

    /**
     * 格式化日期
     *
     * @param localDate
     * @param format
     * @return
     */
    public static String formatDate(LocalDate localDate, String format) {
        if (Objects.isNull(localDate)) {
            return "";
        }
        if (StringUtils.isBlank(format)) {
            return DEFAULT_DATE_FORMATTER.format(localDate);
        } else {
            return DateTimeFormatter.ofPattern(format).format(localDate);
        }
    }

    /**
     * 按照默认Pattern(yyyy-MM-dd)格式化日期
     *
     * @param localDate
     * @return
     */
    public static String formatDate(LocalDate localDate) {
        if (Objects.isNull(localDate)) {
            return "";
        }
        return formatDate(localDate, "yyyy-MM-dd");
    }

    /**
     * 格式化时间
     *
     * @param localDateTime
     * @param format
     * @return
     */
    public static String formatDateTime(LocalDateTime localDateTime, String format) {
        if (Objects.isNull(localDateTime)) {
            return "";
        }
        if (StringUtils.isBlank(format)) {
            return DEFAULT_DATETIME_FORMATTER.format(localDateTime);
        } else {
            return DateTimeFormatter.ofPattern(format).format(localDateTime);
        }
    }

    /**
     * 按照默认Pattern(yyyy-MM-dd HH:mm:ss)格式化时间
     *
     * @param localDateTime
     * @return
     */
    public static String formatDateTime(LocalDateTime localDateTime) {
        if (Objects.isNull(localDateTime)) {
            return "";
        }
        return formatDateTime(localDateTime, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 格式化时刻
     *
     * @param localTime
     * @param format
     * @return
     */
    public static String formatTime(LocalTime localTime, String format) {
        if (Objects.isNull(localTime)) {
            return "";
        }
        if (StringUtils.isBlank(format)) {
            return DEFAULT_TIME_FORMATTER.format(localTime);
        } else {
            return DateTimeFormatter.ofPattern(format).format(localTime);
        }
    }

    /**
     * 按照默认Pattern(yyyy-MM-dd HH:mm:ss)格式化时刻
     *
     * @param localTime
     * @return
     */
    public static String formatTime(LocalTime localTime) {
        if (Objects.isNull(localTime)) {
            return "";
        }
        return formatTime(localTime, "HH:mm:ss");
    }

    /**
     * 时间字符串转为LocalDateTime时间对象 , 默认Pattern(yyyy-MM-dd HH:mm:ss)
     *
     * @param dateTimeStr 时间字符串
     * @param format 格式化Pattern
     * @return
     */
    public static LocalDateTime dateTimeStrToDateTime(String dateTimeStr, String format) {
        if (StringUtils.isBlank(dateTimeStr)) {
            return null;
        }
        LocalDateTime dateTime = null;

        if (StringUtils.isBlank(format)) {
            dateTime = LocalDateTime.parse(dateTimeStr, DEFAULT_DATETIME_FORMATTER);
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            dateTime = LocalDateTime.parse(dateTimeStr, formatter);
        }

        return dateTime;
    }

    /**
     * 按照默认Pattern(yyyy-MM-dd HH:mm:ss)转换为LocalDateTime
     *
     * @param dateTimeStr
     * @return
     */
    public static LocalDateTime dateTimeStrToDateTime(String dateTimeStr) {
        if (StringUtils.isBlank(dateTimeStr)) {
            return null;
        }
        return dateTimeStrToDateTime(dateTimeStr, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 时间字符串转为LocalDate日期对象 , 默认Pattern(yyyy-MM-dd)
     *
     * @param dateStr 日期字符串
     * @param format 格式化Pattern
     * @return
     */
    public static LocalDate dateStrToDate(String dateStr, String format) {
        if (StringUtils.isBlank(dateStr)) {
            return null;
        }
        LocalDate localDate = null;

        if (StringUtils.isBlank(format)) {
            localDate = LocalDate.parse(dateStr, DEFAULT_DATE_FORMATTER);
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            localDate = LocalDate.parse(dateStr, formatter);
        }

        return localDate;
    }

    /**
     * 按照默认Pattern(yyyy-MM-dd)转换为LocalDate
     *
     * @param dateStr
     * @return
     */
    public static LocalDate dateStrToDate(String dateStr) {
        if (StringUtils.isBlank(dateStr)) {
            return null;
        }
        return dateStrToDate(dateStr, "yyyy-MM-dd");
    }

    /**
     * 时刻字符串转为LocalTime日期对象 , 默认Pattern(HH:mm:ss)
     *
     * @param timeStr 时刻字符串
     * @param format 格式化Pattern
     * @return
     */
    public static LocalTime timeStrToTime(String timeStr, String format) {
        if (StringUtils.isBlank(timeStr)) {
            return null;
        }
        LocalTime localTime = null;

        if (StringUtils.isBlank(format)) {
            localTime = LocalTime.parse(timeStr, DEFAULT_TIME_FORMATTER);
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            localTime = LocalTime.parse(timeStr, formatter);
        }

        return localTime;
    }

    /**
     * 按照默认Pattern(HH:mm:ss)转换为LocalTime
     *
     * @param timeStr
     * @return
     */
    public static LocalTime timeStrToTime(String timeStr) {
        if (StringUtils.isBlank(timeStr)) {
            return null;
        }
        return timeStrToTime(timeStr, "HH:mm:ss");
    }

    /**
     * 按照传入的对象和分隔符进行分割、转换，返回泛型列表
     *
     * @param data
     * @param delimiter
     * @param clazz
     * @return
     * @param <T>
     */
    public static <T> List<T> formatArrayItem(Object data, String delimiter, Class<T> clazz) {
        if (StringUtils.isBlank(delimiter)) {
            throw new RuntimeException("parameter delimiter can't be empty.");
        }

        List<T> items = Collections.emptyList();

        if (Objects.isNull(data)) {
            return items;
        }

        if (data instanceof List) {
            items =
                    ((List<?>) data)
                            .stream()
                                    .map(ele -> Convert.convert(clazz, ele))
                                    .collect(Collectors.toList());
        } else if (data instanceof String dataStr) {
            if (StringUtils.isNotBlank(dataStr)) {
                items =
                        Arrays.stream(dataStr.split(delimiter))
                                .map(ele -> Convert.convert(clazz, ele))
                                .collect(Collectors.toList());
            }
        } else {
            // 其他类型，转为T再处理
            items = Collections.singletonList(Convert.convert(clazz, data));
        }

        return items;
    }

    /**
     * * 获取列表对象中的某个属性值为key,另一个属性值作为值列表， 返回Map。 配合实现PHP的同名方法，Java中有更好的实现方式 data.stream()
     * .collect(Collectors.groupingBy( T::getkeyField, Collectors.mapping(T::getcolumnField,
     * Collectors.toList()) ));
     */
    @Deprecated
    public static <T> Map<Object, List<Object>> formatArrayByKeyToMultiArray(
            List<T> data, String keyField, String columnField) {
        if (StringUtils.isBlank(keyField)) {
            return new HashMap<>();
        }

        Map<Object, List<Object>> format = new HashMap<>();

        for (T item : data) {
            try {
                // 获取 key 字段值
                Object keyValue = BeanUtil.getProperty(item, keyField);
                if (keyValue == null) {
                    break;
                }

                Object value;
                if (StringUtils.isNotBlank(columnField)) {
                    value = BeanUtil.getProperty(item, columnField);
                    if (value == null) value = item;
                } else {
                    value = item;
                }

                format.computeIfAbsent(keyValue, k -> new ArrayList<>()).add(value);
            } catch (Exception e) {
                break;
            }
        }

        return format;
    }

    /**
     * 等价 PHP：formatWebsocketData WebSocket 数据编码/解码
     * 实际上没什么必要，可由发送和接收端手动调用Json序列化和反序列化方法，糅合在一个方法，无法返回对象类型。
     * 也可以单独调用formatWebsocketSendData和formatWebsocketReceiveData，指定返回类型
     */
    @Deprecated
    public static <T> T formatWebsocketData(
            Object data, String format, TypeReference<T> returnType) {
        if (Objects.isNull(data)) {
            return null;
        }
        try {
            // 等价 match ($format)
            return switch (format) {
                case "encode" -> {
                    if (data == null) {
                        yield null;
                    }
                    // 等价 json_encode($data, JSON_THROW_ON_ERROR)
                    yield (T) formatWebsocketSendData(data);
                }
                case "decode" -> {
                    // 等价 json_decode($data, true, 512, JSON_THROW_ON_ERROR)
                    yield formatWebsocketReceiveData(data.toString(), returnType);
                }
                default -> null;
            };
        } catch (Exception e) {
            // 捕获 JSON 异常，返回 false
            return null;
        }
    }

    /**
     * websocket发送数据json序列化
     *
     * @param data 待发送数据
     * @return
     * @param <T>
     * @throws JsonProcessingException
     */
    public static <T> String formatWebsocketSendData(T data) {
        try {
            return Objects.isNull(data) ? null : JsonUtil.obj2Json(data);
        } catch (JsonProcessingException e) {
            LOGGER.error("Json序列化出错。");
        }
        return null;
    }

    /**
     * websocket接收数据json反序列化，用TypeReference更安全
     *
     * @param data 接收到的json字符串
     * @param clazz 序列化后的对象类型
     * @return
     * @param <T>
     * @throws JsonProcessingException
     */
    public static <T> T formatWebsocketReceiveData(String data, TypeReference<T> clazz) {
        if (Objects.isNull(data)) {
            return null;
        }
        try {
            return JsonUtil.json2Obj(data, clazz);
        } catch (JsonProcessingException e) {
            LOGGER.error("Json反序列化出错。Json字符串:{}", data);
        }
        return null;
    }

    /**
     * 等价 PHP：formatArrayToKey 将数组/列表按照指定字段转为 key => value 格式的Map value取List中key对应的最后一个值
     * 从PHP翻译过来，实际上没什么用 Java可以使用stream toMap实现 语义更清晰，类型更安全
     *
     * <p>Map<Long, Long> idToProjectIdMap = departVersionRecords.stream()
     * .collect(Collectors.toMap( DepartVersionRecord::getId, DepartVersionRecord::getProjectId,
     * (oldValue, newValue) -> newValue // 保留新值 ));
     */
    @Deprecated
    public static Map<Object, Object> formatArrayToKey(
            List<Map<String, Object>> data, String key, String column) {
        // 如果 key 为空，直接返回原数据（转为Map结构）
        if (CollectionUtils.isEmpty(data) || key == null || key.isEmpty()) {
            Map<Object, Object> result = new HashMap<>();
            for (int i = 0; i < data.size(); i++) {
                result.put(i, data.get(i));
            }
            return result;
        }

        Map<Object, Object> format = new HashMap<>();

        for (Map<String, Object> item : data) {
            // 如果不存在该 key，直接终止循环（和PHP break 行为一致）
            if (!item.containsKey(key)) {
                break;
            }

            Object keyValue = item.get(key);
            Object value;

            // 如果指定了 column 则取对应列，否则取整条数据
            if (column != null && !column.isEmpty()) {
                value = item.getOrDefault(column, item);
            } else {
                value = item;
            }

            format.put(keyValue, value);
        }

        return format;
    }

    // 重载方法：兼容 column 不传的情况
    public static Map<Object, Object> formatArrayToKey(List<Map<String, Object>> data, String key) {
        return formatArrayToKey(data, key, "");
    }

    /** 将字符串按分隔符转为List<Long> 原版本Util方法 ， 可以直接调用formatArrayItem替换掉 不建议使用，可以直接调用formatArrayItem */
    @Deprecated
    public static List<Long> splitToLongList(String str, String delimiter) {
        if (str == null || str.isEmpty()) {
            return new ArrayList<>();
        }
        return formatArrayItem(str, delimiter, Long.class);
    }

    /**
     * 等价 PHP：toUnixTimestamp 原版Util方法，可废弃，数据库时间字段换为DateTime之后大部分场景不再需要转换
     * 不建议使用，数据库字段换为DateTime后不太需要这个转换方法，建议检查使用场景
     */
    @Deprecated
    public static long toUnixTimestamp(Object date) {
        return 0L;
    }

    /**
     * 等价 PHP：unZipCode 解码 base64 编码的 JSON 数据 原版Util方法，数据库字段规范之后，存取不再需要Base64编解码 不建议使用 直接用JsonUtil
     *
     * @param data json字符串
     * @param typeReference 反序列化结果类型
     * @return
     * @param <T>
     */
    @Deprecated
    public static <T> T unZipCode(String data, TypeReference<T> typeReference) {
        if (StringUtils.isBlank(data)) {
            return null;
        }
        try {
            return JsonUtil.json2Obj(data, typeReference);
        } catch (JsonProcessingException e) {
            LOGGER.error("Json反序列化出错。Json字符串:{}", data);
        }
        return null;
    }

    /**
     * 等价 PHP：zipCode 原版Util方法，数据库字段规范之后，存取不再需要Base64编解码 不建议使用 直接用JsonUtil
     *
     * @param columns
     * @return
     */
    @Deprecated
    public static String zipCode(List<?> columns) {
        if (CollectionUtils.isEmpty(columns)) {
            return null;
        }
        try {
            return JsonUtil.obj2Json(columns);
        } catch (Exception e) {
            LOGGER.error("Json序列化出错。");
        }
        return null;
    }

    /**
     * 原来的Util方法 暂时保留 去掉Base编码 可直接调用JsonUtil.obj2Json 不建议使用 直接用JsonUtil
     *
     * @param o
     * @return
     */
    @Deprecated
    public static String zipCodeFromObject(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return JsonUtil.obj2Json(o);
        } catch (Exception e) {
            LOGGER.error("Json序列化出错。");
            return null;
        }
    }

    /**
     * 原来的Util方法 暂时保留 去掉Base解码 可直接调用JsonUtil.json2Obj 不建议使用 直接用JsonUtil
     *
     * @param data
     * @param tr
     * @param ignoreError
     * @return
     * @param <T>
     */
    @Deprecated
    public static <T> T unZipCodeToObject(String data, TypeReference<T> tr, boolean ignoreError) {
        if (StringUtils.isEmpty(data)) {
            return null;
        }
        try {
            return JsonUtil.json2Obj(data, tr);
        } catch (Exception e) {
            LOGGER.error("Json反序列化出错。Json字符串:{}", data);
        }
        return null;
    }

    /** 月份转换为年月格式 Convert months to "X年Y月" format 等价 PHP: ToolUtils::monthsToYears */
    public static String monthsToYears(Integer months) {
        if (months == null || months == 0) {
            return "0年0月";
        }
        int years = months / 12;
        int remainingMonths = months % 12;
        return years + "年" + remainingMonths + "月";
    }

    /**
     * 格式化数组项 Format array item - handles string, list, or single value 等价 PHP:
     * ToolUtils::formatArrayItem (simplified version) 原版Util方法 可以废弃 直接调用 formatArrayItem(data , ","
     * , Class<T> clazz) 不建议使用 直接用formatArrayItem泛型方法
     */
    @Deprecated
    public static List<Object> formatArrayItem(Object data) {
        if (data == null) {
            return new ArrayList<>();
        }

        return formatArrayItem(data, ",", Object.class);
    }

    /** 格式化时间 Format timestamp to string */
    /**
     * 暂时保留 实际上可以废弃 数据库规范之后没有Long类型的时间 可以直接调用ToolUtils.formatDateTime等方法
     *
     * @param timestamp
     * @param format
     * @return
     */
    @Deprecated
    public static String formatTime(Long timestamp, String format) {
        if (timestamp == null || timestamp == 0) {
            return "";
        }

        try {
            Instant instant = Instant.ofEpochSecond(timestamp);
            LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            return ToolUtils.formatDateTime(dateTime, format);
        } catch (Exception e) {
            return "";
        }
    }

    /** 对比两个数组的差异 适用于 k-v 结构 [id => value] */
    public static Map<String, Map<Long, String>> compareArrays(
            Map<Long, String> original, Map<Long, String> current) {
        // 处理 null，默认给空 map
        if (original == null) original = new HashMap<>();
        if (current == null) current = new HashMap<>();

        Map<Long, String> add = new HashMap<>();
        Map<Long, String> sub = new HashMap<>();
        Map<Long, String> diff = new HashMap<>();

        // 1. add：current 有，original 没有
        for (Map.Entry<Long, String> entry : current.entrySet()) {
            Long key = entry.getKey();
            if (!original.containsKey(key)) {
                add.put(key, entry.getValue());
            }
        }

        // 2. sub：original 有，current 没有
        for (Map.Entry<Long, String> entry : original.entrySet()) {
            Long key = entry.getKey();
            if (!current.containsKey(key)) {
                sub.put(key, entry.getValue());
            }
        }

        // 3. diff：key 都存在，但 value 不同
        for (Map.Entry<Long, String> entry : current.entrySet()) {
            Long key = entry.getKey();
            String currentVal = entry.getValue();
            if (original.containsKey(key)) {
                String originalVal = original.get(key);
                if (originalVal != null && !originalVal.equals(currentVal)) {
                    diff.put(key, currentVal);
                }
            }
        }

        Map<String, Map<Long, String>> result = new HashMap<>();
        result.put("add", add);
        result.put("sub", sub);
        result.put("diff", diff);
        return result;
    }

    /** 使用Map优化的树构建方法 */
    public static List<Map<String, Object>> buildNestedArrayOptimized(
            List<Map<String, Object>> nodes,
            Object parentId,
            String primaryKeyName,
            String parentKeyName,
            String childrenKeyName) {

        if (nodes == null || nodes.isEmpty()) {
            return new ArrayList<>();
        }

        primaryKeyName = primaryKeyName != null ? primaryKeyName : "id";
        parentKeyName = parentKeyName != null ? parentKeyName : "parent_id";
        childrenKeyName = childrenKeyName != null ? childrenKeyName : "children";

        // 构建ID到节点的映射，便于快速查找
        Map<Object, List<Map<String, Object>>> map = new HashMap<>();
        for (Map<String, Object> node : nodes) {
            Object parentKey = node.get(parentKeyName);
            map.computeIfAbsent(parentKey, k -> new ArrayList<>()).add(node);
        }

        return buildTreeRecursive(map, parentId, primaryKeyName, childrenKeyName, 0);
    }

    private static List<Map<String, Object>> buildTreeRecursive(
            Map<Object, List<Map<String, Object>>> map,
            Object parentId,
            String primaryKeyName,
            String childrenKeyName,
            int level) {

        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> nodes = map.get(parentId);

        if (nodes == null) {
            return result;
        }

        for (Map<String, Object> node : nodes) {
            Map<String, Object> newNode = new HashMap<>(node);
            newNode.put("level", level);

            // 递归获取子节点
            List<Map<String, Object>> children =
                    buildTreeRecursive(
                            map,
                            node.get(primaryKeyName),
                            primaryKeyName,
                            childrenKeyName,
                            level + 1);

            newNode.put(childrenKeyName, children);
            result.add(newNode);
        }

        return result;
    }

    /**
     * 调用外部系统时，先json序列化再Base64编码 对应PHP的ToolUtils.zipCode
     *
     * @param data
     * @return
     */
    public static Object zipCodeForExternal(Object data) {
        if (data == null) {
            return null;
        }
        String jsonStr = JSONUtil.toJsonStr(data);
        if (StrUtil.isBlank(jsonStr)) {
            return null;
        }
        return Base64.getEncoder().encodeToString(jsonStr.getBytes(StandardCharsets.UTF_8));
    }

    // 使用泛型构造树结构，去掉之前实体类Map传参，用反射获取属性值
    public static <T> List<T> buildTree(List<T> list, Object rootId, String idKey, String pidKey) {
        return buildTreeRecursive(list, rootId, idKey, pidKey, 0);
    }

    private static <T> List<T> buildTreeRecursive(
            List<T> list, Object rootId, String idKey, String pidKey, int level) {
        List<T> result = new ArrayList<>();
        Field[] fields = null;
        for (T node : list) {
            // 仅第一次需要获取
            if (fields == null || fields.length <= 0) {
                fields = node.getClass().getDeclaredFields();
            }

            Field pidField =
                    Arrays.stream(fields)
                            .filter(f -> pidKey.equals(f.getName()))
                            .findFirst()
                            .orElse(null);
            Object pid = null;
            if (Objects.nonNull(pidField)) {
                try {
                    pidField.setAccessible(true);
                    pid = pidField.get(node);
                } catch (IllegalAccessException e) {
                    LOGGER.warn("field:{}没有访问权限", pidKey);
                }
            }
            boolean isMatch = false;
            if (pid == null && rootId == null) {
                isMatch = true;
            } else if (pid != null && rootId != null) {
                if (pid instanceof Number && rootId instanceof Number) {
                    isMatch = ((Number) pid).longValue() == ((Number) rootId).longValue();
                } else {
                    isMatch = Objects.equals(pid.toString(), rootId.toString());
                }
            }

            if (isMatch) {
                // PHP: $node['level'] = ...;
                Field levelField =
                        Arrays.stream(fields)
                                .filter(f -> "level".equals(f.getName()))
                                .findFirst()
                                .orElse(null);
                if (Objects.nonNull(levelField)) {
                    try {
                        levelField.setAccessible(true);
                        levelField.set(node, level);
                    } catch (IllegalAccessException e) {
                        LOGGER.warn("field:{}没有访问权限", level);
                    }
                }
                // PHP: $node['children'] = buildNestedArray(..., $level + 1);
                Field idField =
                        Arrays.stream(fields)
                                .filter(f -> idKey.equals(f.getName()))
                                .findFirst()
                                .orElse(null);
                if (Objects.nonNull(idField)) {
                    try {
                        idField.setAccessible(true);
                        List<T> children =
                                buildTreeRecursive(
                                        list, idField.get(node), idKey, pidKey, level + 1);
                        Field childrenField =
                                Arrays.stream(fields)
                                        .filter(f -> "children".equals(f.getName()))
                                        .findFirst()
                                        .orElse(null);
                        if (Objects.nonNull(childrenField)) {
                            try {
                                childrenField.setAccessible(true);
                                childrenField.set(node, children);
                            } catch (IllegalAccessException e) {
                                LOGGER.warn("field:{}没有访问权限", children);
                            }
                        }
                    } catch (IllegalAccessException e) {
                        LOGGER.warn("field:{}没有访问权限", idKey);
                    }
                }

                result.add(node);
            }
        }
        return result;
    }

    public static String camelToSnakeCase(String camel) {
        if (camel == null || camel.isEmpty()) return camel;
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toLowerCase(camel.charAt(0)));
        for (int i = 1; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String snakeToCamelCase(String snake) {
        if (snake == null || snake.isEmpty()) return snake;
        String[] parts = snake.split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].length() > 0) {
                sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }

    /**
     * 场景2：递归深度遍历（已经组装好带children嵌套的树形结构）
     *
     * @param treeList 顶层树形集合
     * @param targetId 要查找的节点ID
     * @return 匹配节点，找不到返回null
     */
    public static ModuleNodeResp findNodeByTreeRecursion(
            List<ModuleNodeResp> treeList, Long targetId) {
        for (ModuleNodeResp node : treeList) {
            // 当前节点匹配直接返回
            if (targetId.equals(node.getId())) {
                return node;
            }
            // 当前节点存在子节点，递归往下查找
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                ModuleNodeResp childResult = findNodeByTreeRecursion(node.getChildren(), targetId);
                if (childResult != null) {
                    return childResult;
                }
            }
        }
        // 整棵树未找到
        return null;
    }

    /**
     * 将目标节点自身 + 所有层级子节点 平铺存入一维List
     *
     * @param targetNode 起始节点
     * @param result 输出平铺集合
     */
    public static void flatNodeAndChildren(ModuleNodeResp targetNode, List<ModuleNodeResp> result) {
        if (targetNode == null) {
            return;
        }
        // 先加入自己
        result.add(targetNode);
        List<ModuleNodeResp> children = targetNode.getChildren();
        if (children == null || children.isEmpty()) {
            return;
        }
        // 递归遍历所有子节点
        for (ModuleNodeResp child : children) {
            flatNodeAndChildren(child, result);
        }
    }

    /** 第一步：递归在树形列表中根据ID找到目标节点 */
    public static <T> List<T> findTargetNode(List<T> treeList, Long targetId, String idKey) {
        List<T> result = new ArrayList<>();
        Field[] fields = null;
        for (T node : treeList) {
            // 仅第一次需要获取
            if (fields == null || fields.length <= 0) {
                fields = node.getClass().getDeclaredFields();
            }

            Field idField =
                    Arrays.stream(fields)
                            .filter(f -> idKey.equals(f.getName()))
                            .findFirst()
                            .orElse(null);
            Object id = null;
            if (Objects.nonNull(idField)) {
                try {
                    idField.setAccessible(true);
                    id = idField.get(node);
                } catch (IllegalAccessException e) {
                    LOGGER.warn("field:{}没有访问权限", id);
                }
            }

            if (targetId.equals(id)) {
                result.add(node);
            }

            Field childrenField =
                    Arrays.stream(fields)
                            .filter(f -> "children".equals(f.getName()))
                            .findFirst()
                            .orElse(null);

            List<T> children = null;
            if (Objects.nonNull(childrenField)) {
                try {
                    childrenField.setAccessible(true);
                    if (Objects.nonNull(childrenField.get(node))) {
                        children = (List<T>) childrenField.get(node);
                    }
                } catch (IllegalAccessException e) {
                    LOGGER.warn("field:{}没有访问权限", children);
                }
            }

            if (children != null && !CollectionUtils.isEmpty(children)) {
                List<T> child = findTargetNode(children, targetId, "id");
                if (child != null) {
                    result.addAll(child);
                }
            }
        }
        return result;
    }

    public static Boolean equalsLongValue(Long v1, Long v2) {
        if (v1 == null || v2 == null) {
            return false;
        }
        return v1.equals(v2);
    }
}
