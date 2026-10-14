package com.jdec.platform.dataengine.biz.third.api.persistence.tab;

import com.jdec.platform.dataengine.biz.dto.DataSaveResult;
import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import java.util.List;
import org.jooq.DSLContext;

public interface BaseSingleTablePersistenceService {

    DataSaveResult insertRecord(RecordSaveContext context);

    DataSaveResult updateRecord(RecordSaveContext context);

    DataSaveResult saveRecord(RecordSaveContext context);

    List<Long> getRecordIdsByFk(
            DSLContext dslContext, String tableName, String relatedField, List<Long> ids);

    int deleteByIds(DSLContext dslContext, String tableName, List<Long> ids);
}
