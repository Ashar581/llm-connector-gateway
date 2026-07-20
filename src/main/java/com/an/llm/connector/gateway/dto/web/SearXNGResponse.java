package com.an.llm.connector.gateway.dto.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SearXNGResponse(List<SearXNGResult> results) {
}
