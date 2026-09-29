package com.jdec.platform.dataengine.biz.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import org.jooq.JSONB;

public class JooqJSONBSerializer extends JsonSerializer<JSONB> {
    @Override
    public void serialize(JSONB value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeRawValue(value.data());
        // 如果想要输出字符串包裹：gen.writeString(value.data());
    }
}
