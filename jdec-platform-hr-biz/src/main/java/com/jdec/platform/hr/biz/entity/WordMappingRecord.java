package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 词汇映射记录 */
@TableName("word_mapping_record")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WordMappingRecord extends BaseHrEntity {
    private Integer parentModuleId;
    private Integer moduleId;
    private String moduleSlug;
    private Integer businessId;
    private String columnSlug;
    private Integer wordId;
    private String wordNo;
    private String wordValue;
    private String path;
    private String groupHash;
}
