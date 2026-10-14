package com.jdec.platform.approval.api.resp;

import com.jdec.platform.approval.api.dto.ApprpvalIdButton;
import java.util.List;
import lombok.Data;

@Data
public class ApprovalIdButtonListResp {
    List<ApprpvalIdButton> toDoList;
    List<ApprpvalIdButton> DoneList;
    List<ApprpvalIdButton> AllList;
}
