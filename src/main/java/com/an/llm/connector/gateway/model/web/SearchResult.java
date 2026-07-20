package com.an.llm.connector.gateway.model.web;

public record SearchResult(String title, String url, String snippet, Double score) {
}
