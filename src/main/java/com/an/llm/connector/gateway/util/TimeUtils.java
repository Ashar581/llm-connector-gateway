package com.an.llm.connector.gateway.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public interface TimeUtils extends TimeFormats {
    String EXCEPTION_RESPONSE_FORMAT = LocalDateTime.now().format(DateTimeFormatter.ofPattern(ERROR_TIME_FORMAT));
}
