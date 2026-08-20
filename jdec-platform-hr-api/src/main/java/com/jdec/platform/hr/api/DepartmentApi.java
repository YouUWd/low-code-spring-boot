package com.jdec.platform.hr.api;

import com.jdec.platform.hr.api.bo.DepartmentBO;
import java.util.List;

public interface DepartmentApi {
    List<DepartmentBO> getDepartmentList();
}
