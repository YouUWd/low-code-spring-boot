package com.jdec.platform.dataengine.biz.service.impl;

import com.jdec.platform.dataengine.biz.service.SequenceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@AllArgsConstructor
public class SequenceServiceImpl implements SequenceService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public Long nextSequenceValue(String seqName) {
        // 1. 悲观锁查询该name的序号记录
        String selectSql = "SELECT seq_value FROM biz_seq WHERE seq_name = ? FOR UPDATE";
        Long currNo;
        try {
            currNo = jdbcTemplate.queryForObject(selectSql, Long.class, seqName);
        } catch (EmptyResultDataAccessException e) {
            currNo = null;
        }

        // 2. 不存在，初始化这条序列，curr_no=0
        if (currNo == null) {
            try {
                jdbcTemplate.update(
                        "INSERT INTO biz_seq(seq_name, seq_value) VALUES (?, 0)", seqName);
                currNo = 0L;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发：别的线程已经初始化成功，重新锁查询
                currNo = jdbcTemplate.queryForObject(selectSql, Long.class, seqName);
            }
        }

        // 3. 序号+1
        Long nextNo = currNo + 1;
        // 4. 更新序列号
        jdbcTemplate.update(
                "UPDATE biz_seq SET seq_value = ?,updated_date=? WHERE seq_name = ?",
                nextNo,
                new java.util.Date(),
                seqName);

        return nextNo;
    }
}
