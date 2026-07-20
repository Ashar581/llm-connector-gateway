package com.an.llm.connector.gateway.model.web;

import java.util.List;

public record SearchResponse(List<SearchResult> results) {
}
