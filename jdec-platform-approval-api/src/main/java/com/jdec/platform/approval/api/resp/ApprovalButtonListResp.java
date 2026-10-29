package com.jdec.platform.approval.api.resp;

import java.util.List;
import lombok.Data;

@Data
public class ApprovalButtonListResp {
    List<String> toDoList;
    List<String> DoneList;
    List<String> AllList;
}
