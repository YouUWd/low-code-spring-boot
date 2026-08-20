package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 领用物品 */
@TableName("user_item")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserItem extends BaseHrEntity {
    private Integer userId;
    private Integer typeId;
    private Integer classId;
    private String stockNumber;
    private String stockName;
    private String stockModel;
    private String stockUnit;
    private Integer consumingDate;
    private Integer consumingNum;
    private String code;
    private String note;
    private String groupHash;
}
