package com.jdec.platform.approval.api.resp;

import com.jdec.platform.approval.api.dto.ApprovalIdButton;
import java.util.List;
import lombok.Data;

@Data
public class ApprovalIdButtonListResp {
    List<ApprovalIdButton> toDoList;
    List<ApprovalIdButton> DoneList;
    List<ApprovalIdButton> AllList;
}
