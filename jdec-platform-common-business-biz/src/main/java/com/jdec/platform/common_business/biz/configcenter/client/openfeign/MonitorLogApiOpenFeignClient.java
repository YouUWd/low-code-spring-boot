package com.jdec.platform.common_business.biz.configcenter.client.openfeign;

import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.monitorlog.api.dto.request.MonitorLogReq;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "monitor-log",
        url = "http://127.0.0.1:8080/api/monitorlog",
        configuration = FeignConfig.class)
public interface MonitorLogApiOpenFeignClient {

    @PostMapping("/addBusinessChangeLog")
    ConfigApiResponse<Void> add(@RequestBody MonitorLogReq request);
}
