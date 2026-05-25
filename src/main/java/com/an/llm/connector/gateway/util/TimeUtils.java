package com.an.llm.connector.gateway.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public interface TimeUtils extends TimeFormats {
    String EXCEPTION_RESPONSE_FORMAT = LocalDateTime.now().format(DateTimeFormatter.ofPattern(ERROR_TIME_FORMAT));
    default String getLlmConfigUtcPostfix(){
        return DateTimeFormatter.ofPattern(UNIVERSAL_UTC_POSTFIX).withZone(ZoneOffset.UTC).format(Instant.now());
    }
}
