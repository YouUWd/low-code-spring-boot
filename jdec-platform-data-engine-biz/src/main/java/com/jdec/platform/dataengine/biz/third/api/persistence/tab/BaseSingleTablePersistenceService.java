package com.jdec.platform.dataengine.biz.third.api.persistence.tab;

import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;

public interface BaseSingleTablePersistenceService {

    Long insertRecord(RecordSaveContext context);

    Long updateRecord(RecordSaveContext context);

    Long saveRecord(RecordSaveContext context);
}
