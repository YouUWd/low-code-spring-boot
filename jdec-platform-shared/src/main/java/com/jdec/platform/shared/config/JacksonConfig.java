package com.jdec.platform.shared.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper =
                builder.createXmlMapper(false)
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .dateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                        .deserializerByType(
                                LocalDateTime.class,
                                new LocalDateTimeDeserializer(
                                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                        .deserializerByType(
                                LocalDate.class,
                                new LocalDateDeserializer(
                                        DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                        .serializerByType(
                                LocalDateTime.class,
                                new LocalDateTimeSerializer(
                                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                        .serializerByType(
                                LocalDate.class,
                                new LocalDateSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                        .build();
        //        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        //        JavaTimeModule module = new JavaTimeModule();
        //        module.addDeserializer(
        //                LocalDateTime.class,
        //                new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd
        // HH:mm:ss")));
        //        module.addDeserializer(
        //                LocalDate.class,
        //                new LocalDateDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        //        module.addSerializer(
        //                LocalDateTime.class,
        //                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd
        // HH:mm:ss")));
        //        module.addSerializer(
        //                LocalDate.class,
        //                new LocalDateSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        //        objectMapper.registerModule(module);
        return objectMapper;
    }
}
