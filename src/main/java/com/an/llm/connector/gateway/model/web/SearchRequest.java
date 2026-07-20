package com.an.llm.connector.gateway.model.web;

import java.time.Duration;

public record SearchRequest(String query, Integer maxResults, Duration timeout) {}
