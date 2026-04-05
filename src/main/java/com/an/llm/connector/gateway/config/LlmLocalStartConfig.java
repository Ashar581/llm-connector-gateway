package com.an.llm.connector.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.Socket;

@Slf4j
@Configuration
public class LlmLocalStartConfig {

    @Bean
    public ApplicationRunner startLlmLocally() {
        return args -> {
            ProcessBuilder builder = new ProcessBuilder(
                    "bash", "-c",
                    "cd ~/llama.cpp && ./build/bin/llama-server " +
                            "-m models/qwen2.5-7b-instruct-q4_0-00001-of-00002.gguf " +
                            "-c 4096 -np 8 -t 8 -cb -ngl 999 " +
                            "--host 0.0.0.0 --port 8080"
            );

            if (!isLlmRunningLocally()) {
                builder.inheritIO();
                builder.start();
            }
        };
    }

    public boolean isLlmRunningLocally() {
        try (Socket socket = new Socket("localhost", 8080)) {
            log.info("LLM already running locally.");
            return true;
        } catch (Exception e) {
            log.info("LLM needs to be started");
            return false;
        }
    }
}
