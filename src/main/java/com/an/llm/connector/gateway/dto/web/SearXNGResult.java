package com.an.llm.connector.gateway.dto.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SearXNGResult(String title, String url, String content, Double score) {
}
