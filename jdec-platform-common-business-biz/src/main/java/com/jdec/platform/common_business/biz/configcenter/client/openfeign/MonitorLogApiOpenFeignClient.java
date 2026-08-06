package com.jdec.platform.common_business.biz.configcenter.client.openfeign;

import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.monitorlog.api.dto.request.MonitorControlLogReq;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogPageListResp;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "monitor-log",
        url = "${config.center.base}",
        path = "/api/monitorlog",
        configuration = FeignConfig.class)
public interface MonitorLogApiOpenFeignClient {

    @PostMapping("/addControlLog")
    ConfigApiResponse<Void> recordBusinessLog(@RequestBody MonitorControlLogReq request);

    @GetMapping("/querySystemControlLogByModuleIdAndDataId")
    public ConfigApiResponse<List<MonitorLogPageListResp>> querySystemControlLogByModuleIdAndDataId(
            @RequestParam Long moduleId,
            @RequestParam Long dataId,
            @Parameter(description = "日志类型(messagelog/controllog/systemlog，不传则查全表)")
                    @RequestParam(required = false)
                    String logType);
}
