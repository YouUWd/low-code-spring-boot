package com.jdec.platform.common_business.biz.configcenter.client.openfeign;

import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.hr.api.bo.UserBO;
import java.util.List;
import java.util.Set;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "hr-center",
        url = "${config.center.base}",
        path = "/api/hr",
        configuration = FeignConfig.class)
public interface HrUserApiOpenFeignClient {

    @PostMapping("/users/list-by-ids")
    public ConfigApiResponse<List<UserBO>> listByIds(@RequestBody Set<Long> userIds);
}
