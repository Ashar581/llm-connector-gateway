package com.an.llm.connector.gateway.config;

import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.enums.Source;
import com.an.llm.connector.gateway.model.config.ModelConfig;
import com.an.llm.connector.gateway.model.config.SourceConfig;
import com.an.llm.connector.gateway.service.LlmConfigService;
import com.an.llm.connector.gateway.util.ProcessBuilderUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.Socket;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class LlmLocalStartConfig {
    private final LlmConfigService llmConfigService;

    @Bean
    public ApplicationRunner startLlmLocally() {
        return args -> {
            log.info("Starting activated LLMs.");
            SourceConfig sourceConfig = llmConfigService.getModelConfigBySource(Source.FREE);

            if (sourceConfig!=null) {
                List<ModelConfig> modelConfigs = sourceConfig.getModels();
                if (modelConfigs!=null && !modelConfigs.isEmpty()) {
                    for (ModelConfig config : modelConfigs) {
                        try {
                            if (config.getActive() != null && config.getActive()) {
                                log.info("Processing model: {}. Listening on port: {}. Adjusted context: {}. Allowed parallel: {}", config.getModelName(), config.getPort(), config.getContext(), config.getParallelExecution());
                                boolean isEmbedding = config.getType().contains(LlmCapability.EMBEDDING.getValue());
                                boolean isVision = config.getType().contains(LlmCapability.VISION.getValue());

                                ProcessBuilder modelBuilder;

//                                ProcessBuilder modelBuilder = new ProcessBuilder(
//                                        "bash",
//                                        "-c",
//                                        isEmbedding
//                                                ?
//                                                ProcessBuilderUtils.generateProcessBuilderEmbedScript(
//                                                        config.getModelName(),
//                                                        config.getContext(),
//                                                        config.getParallelExecution(),
//                                                        config.getPort()
//                                                )
//                                                :
//                                                ProcessBuilderUtils.generateProcessBuilderScript(
//                                                        config.getModelName(),
//                                                        config.getContext(),
//                                                        config.getParallelExecution(),
//                                                        config.getPort()
//                                                )
//                                );

                                if (isVision){
                                    modelBuilder = new ProcessBuilder(
                                            "bash",
                                            "-c",
                                            ProcessBuilderUtils.generateProcessBuilderVlScript(
                                                    config.getModelName(),
                                                    config.getMmProj(),
                                                    config.getContext(),
                                                    config.getParallelExecution(),
                                                    config.getPort()
                                            )
                                    );
                                } else if (isEmbedding) {
                                    modelBuilder = new ProcessBuilder(
                                            "bash",
                                            "-c",
                                            ProcessBuilderUtils.generateProcessBuilderEmbedScript(
                                                    config.getModelName(),
                                                    config.getContext(),
                                                    config.getParallelExecution(),
                                                    config.getPort()
                                            )
                                    );
                                } else {
                                    modelBuilder = new ProcessBuilder(
                                            "bash",
                                            "-c",
                                            ProcessBuilderUtils.generateProcessBuilderScript(
                                                    config.getModelName(),
                                                    config.getContext(),
                                                    config.getParallelExecution(),
                                                    config.getPort()
                                            )
                                    );
                                }
                                if (isLlmRunningLocally(config.getPort())) {
                                    log.info("Model : {} processing was not started. Port: {} is already running. Either the model is already running or some other application is running in the port.", config.getModelName(), config.getPort());
                                    continue;
                                }
                                modelBuilder.inheritIO();
                                modelBuilder.start();

                                try {
                                    Thread.sleep(30000);
                                } catch (Exception ignore) {
                                }

                            } else {
                                log.info("Model: {} will not run since it was disable.", config.getModelName());
                            }
                        }catch (Exception e){
                            log.error("Error while having processing LLM.",e);
                        }
                    }
                }
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
