package com.jdec.platform.dataengine.biz.dto;

import java.io.Serializable;
import lombok.Data;

@Data
public class DataSnapshotDTO implements Serializable {

    /**
     * [ { "mid":123, "pid":0, "mData":{
     * "student_base":{"id":1234,"name":"zhang","sex":1,"birth_date":"2000-01-01"},
     * "student_detail":{"id":234,"student_id":1234,"address":"wuhan","school":"xxx"},
     * "student_xxxx":[{"id":1000,"student_id":1234,"xxx":"wuhan","xxx":"xxx"},{"id":1001,"student_id":1234,"xxx":"wuhan","xxx":"xxx"}],
     * } }, { "mid":334, "pid":123, "mData":{
     * "student_score_base":[{"id":333,"student_id":1234,"course":"中文","score":99},{"id":444,"student_id":1234,"course":"数学","score":88}],
     * "student_score_dtail":[{"id":222,"address":"wuhan","school":"xxx"},{"id":111,"address":"wuhan","school":"xxx"}],
     * "student_score_xxx":[{"id":222,"xxxx":"wuhan","xxxxx":"xxx"},{"id":111,"xxxx":"wuhan","xxxxx":"xxx"},{"id":222,"xxxx":"wuhan","xxxxx":"xxx"},{"id":111,"xxxx":"wuhan","xxxxx":"xxx"}]
     * } } ]
     */
}
