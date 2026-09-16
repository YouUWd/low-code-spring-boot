package com.jdec.platform.dataengine.biz.third.api.persistence.tab;

import com.jdec.platform.dataengine.biz.dto.DataSaveResult;
import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;

public interface BaseSingleTablePersistenceService {

    DataSaveResult insertRecord(RecordSaveContext context);

    DataSaveResult updateRecord(RecordSaveContext context);

    DataSaveResult saveRecord(RecordSaveContext context);
}
