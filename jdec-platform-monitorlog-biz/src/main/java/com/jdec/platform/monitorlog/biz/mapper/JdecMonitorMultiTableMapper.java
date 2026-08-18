package com.jdec.platform.monitorlog.biz.mapper;

import com.jdec.platform.monitorlog.biz.entity.JdecMonitorControlLog;
import com.jdec.platform.monitorlog.biz.entity.JdecMonitorLogBase;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface JdecMonitorMultiTableMapper {

    List<JdecMonitorLogBase> queryPageListWithMultiTable(
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("offset") Long offset,
            @Param("pageSize") Long pageSize,
            @Param("keyWord") String keyWord,
            @Param("logType") String logType);

    long countPageListWithMultiTable(
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("keyWord") String keyWord,
            @Param("logType") String logType);

    List<JdecMonitorControlLog> queryByModuleIdAndOrderId(
            @Param("moduleId") Long moduleId,
            @Param("orderId") String orderId,
            @Param("logType") String logType);

    List<JdecMonitorControlLog> queryByMenuIdAndDataId(
            @Param("menuId") Long menuId,
            @Param("dataId") Long dataId,
            @Param("logType") String logType);

    List<JdecMonitorControlLog> querySystemControlLogByModuleIdAndDataId(
            @Param("moduleId") Long moduleId, @Param("dataId") Long dataId);
}
