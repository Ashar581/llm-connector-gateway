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
            ProcessBuilder llm = new ProcessBuilder(
                    "bash", "-c",
                    "cd ~/llama.cpp && ./build/bin/llama-server " +
                            "-m models/qwen2.5-7b-instruct-q4_0-00001-of-00002.gguf " +
                            "-c 4096 -np 8 -t 8 -cb -ngl 999 " +
                            "--host 0.0.0.0 --port 8080"
            );

            ProcessBuilder embed = new ProcessBuilder(
                    "bash", "-c",
                    "cd ~/llama.cpp && ./build/bin/llama-server " +
                            "-m models/nomic-embed-text-v1.5.Q4_K_M.gguf " +
                            "-c 4096 -np 4 -t 4 -cb -ngl 512 " +
                            "--host 0.0.0.0 --port 8081 --embeddings"
            );

            if (!isLlmRunningLocally(8080)) {
                llm.inheritIO();
                llm.start();
            }

            if (!isLlmRunningLocally(8081)) {
                embed.inheritIO();
                embed.start();
            }
        };
    }

    public boolean isLlmRunningLocally(int port) {
        try (Socket socket = new Socket("localhost", port)) {
            log.info("LLM already running locally on port {}.",port);
            return true;
        } catch (Exception e) {
            log.info("LLM needs to be started on port {}",port);
            return false;
        }
    }
}
