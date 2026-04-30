package com.jdec.platform.config.biz.controller;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
@RestController
@RequestMapping("/api/data/query")
@RequiredArgsConstructor
public class DataQueryController {

    private final DataSource dataSource;

    /**
     * 执行自定义 SQL 查询
     * 
     * @param request SQL 查询请求
     * @return 查询结果
     */
    @PostMapping("/execute")
    public ApiResponse<List<Map<String, Object>>> executeQuery(@RequestBody SqlQueryRequest request) {
        log.info("执行 SQL 查询: {}", request.getSql());
        
        // 安全检查：只允许 SELECT 查询
        String sql = request.getSql().trim().toUpperCase();
        if (!sql.startsWith("SELECT")) {
            return ApiResponse.error("只允许执行 SELECT 查询");
        }
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(request.getSql())) {
            
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnLabel(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                results.add(row);
            }
            
            log.info("查询成功，返回 {} 条记录", results.size());
            return ApiResponse.success(results);
            
        } catch (Exception e) {
            log.error("查询失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 测试数据源连接
     * 
     * @return 数据库连接信息
     */
    @GetMapping("/test-connection")
    public ApiResponse<Map<String, Object>> testConnection() {
        log.info("测试数据源连接");
        
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            Map<String, Object> info = new HashMap<>();
            info.put("database", conn.getCatalog());
            info.put("version", metaData.getDatabaseProductVersion());
            info.put("url", metaData.getURL());
            info.put("username", metaData.getUserName());
            info.put("status", "connected");
            
            log.info("连接测试成功: {}", info.get("database"));
            return ApiResponse.success(info);
            
        } catch (Exception e) {
            log.error("连接测试失败: {}", e.getMessage(), e);
            return ApiResponse.error("连接测试失败: " + e.getMessage());
        }
    }

    /**
     * 查询表列表
     * 
     * @return 表名列表
     */
    @GetMapping("/tables")
    public ApiResponse<List<String>> listTables() {
        log.info("查询表列表");
        
        List<String> tables = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            
            try (ResultSet rs = metaData.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    tables.add(tableName);
                }
            }
            
            log.info("查询到 {} 张表", tables.size());
            return ApiResponse.success(tables);
            
        } catch (Exception e) {
            log.error("查询表列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询表列表失败: " + e.getMessage());
        }
    }

    /**
     * 查询表结构
     * 
     * @param tableName 表名
     * @return 表结构信息
     */
    @GetMapping("/table-structure")
    public ApiResponse<List<Map<String, Object>>> getTableStructure(@RequestParam String tableName) {
        log.info("查询表结构: {}", tableName);
        
        List<Map<String, Object>> columns = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            
            try (ResultSet rs = metaData.getColumns(catalog, null, tableName, "%")) {
                while (rs.next()) {
                    Map<String, Object> column = new HashMap<>();
                    column.put("name", rs.getString("COLUMN_NAME"));
                    column.put("type", rs.getString("TYPE_NAME"));
                    column.put("size", rs.getInt("COLUMN_SIZE"));
                    column.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    column.put("defaultValue", rs.getString("COLUMN_DEF"));
                    column.put("comment", rs.getString("REMARKS"));
                    columns.add(column);
                }
            }
            
            log.info("查询到 {} 个字段", columns.size());
            return ApiResponse.success(columns);
            
        } catch (Exception e) {
            log.error("查询表结构失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询表结构失败: " + e.getMessage());
        }
    }

    /**
     * 快速查询表数据
     * 
     * @param tableName 表名
     * @param limit 限制条数
     * @return 表数据
     */
    @GetMapping("/table-data")
    public ApiResponse<List<Map<String, Object>>> getTableData(
            @RequestParam String tableName,
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("查询表数据: {}, limit={}", tableName, limit);
        
        String sql = String.format("SELECT * FROM %s LIMIT %d", tableName, limit);
        
        SqlQueryRequest request = new SqlQueryRequest();
        request.setSql(sql);
        
        return executeQuery(request);
    }

    /**
     * SQL 查询请求
     */
    public static class SqlQueryRequest {
        private String sql;

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }
    }

    /**
     * API 响应封装
     */
    public static class ApiResponse<T> {
        private int status;
        private String msg;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            ApiResponse<T> response = new ApiResponse<>();
            response.status = 200;
            response.msg = "ok";
            response.data = data;
            return response;
        }

        public static <T> ApiResponse<T> error(String msg) {
            ApiResponse<T> response = new ApiResponse<>();
            response.status = 500;
            response.msg = msg;
            return response;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }

        public T getData() {
            return data;
        }

        public void setData(T data) {
            this.data = data;
        }
    }
}
