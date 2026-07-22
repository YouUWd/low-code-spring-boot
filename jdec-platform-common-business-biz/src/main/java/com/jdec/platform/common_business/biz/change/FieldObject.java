package com.jdec.platform.common_business.biz.change;

import java.io.Serializable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FieldObject implements Serializable {

    String dataId;

    String dataName;
}
