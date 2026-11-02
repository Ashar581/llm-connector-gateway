package com.an.llm.connector.gateway.controller.ui;

import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;

@Controller
public class N8nProxyController {

    private final RestTemplate restTemplate = new RestTemplate();

    @RequestMapping(value = "/n8n/**", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.OPTIONS, RequestMethod.HEAD
    })
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {

        // Extract path after /n8n
        String path = request.getRequestURI().substring("/n8n".length());
        if (path == null || path.isEmpty()) {
            path = "/";
        }

        // Build target URL
        String targetUrl = "http://localhost:5678" + path;
        if (request.getQueryString() != null) {
            targetUrl += "?" + request.getQueryString();
        }

        // Forward headers
        HttpHeaders headers = new HttpHeaders();
        Collections.list(request.getHeaderNames()).forEach(name -> {
            if (!name.equalsIgnoreCase("host") &&
                    !name.equalsIgnoreCase("connection") &&
                    !name.equalsIgnoreCase("upgrade") &&
                    !name.equalsIgnoreCase("keep-alive") &&
                    !name.equalsIgnoreCase("transfer-encoding")) {
                headers.put(name, Collections.list(request.getHeaders(name)));
            }
        });
        headers.set("host", "localhost:5678");

        // Forward body
        byte[] body = null;
        if (request.getContentLength() > 0) {
            body = StreamUtils.copyToByteArray(request.getInputStream());
        }

        HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    targetUrl,
                    HttpMethod.valueOf(request.getMethod()),
                    entity,
                    byte[].class
            );

            // CRITICAL FIX: Rebuild response headers properly
            byte[] responseBody = response.getBody();
            HttpHeaders responseHeaders = new HttpHeaders();

            // Copy headers, removing problematic ones
            response.getHeaders().forEach((key, value) -> {
                if (!key.equalsIgnoreCase("transfer-encoding") &&
                        !key.equalsIgnoreCase("connection") &&
                        !key.equalsIgnoreCase("keep-alive")) {
                    responseHeaders.put(key, value);
                }
            });

            // Ensure Content-Length is set (this fixes the ngrok error)
            if (responseBody != null && responseBody.length > 0) {
                responseHeaders.setContentLength(responseBody.length);
            }

            return new ResponseEntity<>(responseBody, responseHeaders, response.getStatusCode());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(502)
                    .body(("Proxy error: " + e.getMessage()).getBytes());
        }
    }
}

