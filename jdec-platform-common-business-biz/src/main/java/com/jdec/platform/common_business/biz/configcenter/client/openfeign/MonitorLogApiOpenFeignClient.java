package com.jdec.platform.common_business.biz.configcenter.client.openfeign;

import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.monitorlog.api.dto.request.MonitorControlLogReq;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "monitor-log",
        url = "${config.center.base}",
        path = "/api/monitorlog",
        configuration = FeignConfig.class)
public interface MonitorLogApiOpenFeignClient {

    @PostMapping("/addOrderRunLog")
    ConfigApiResponse<Void> recordBusinessLog(@RequestBody MonitorControlLogReq request);
}
