package com.an.llm.connector.gateway.service;

import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.enums.Source;
import com.an.llm.connector.gateway.exception.NotActivatedException;
import com.an.llm.connector.gateway.exception.NotAvailableException;
import com.an.llm.connector.gateway.exception.NotFoundException;
import com.an.llm.connector.gateway.model.config.LlmConfig;
import com.an.llm.connector.gateway.model.config.ModelConfig;
import com.an.llm.connector.gateway.model.config.SourceConfig;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmConfigService {
    private final LlmConfig llmConfig;
    private Map<String, Map<String,ModelConfig>> allModels;

    @PostConstruct
    private void init() {
        Map<String, SourceConfig> sources = getAvailableModels();
        if (sources!=null) {

            Map<String,Map<String,ModelConfig>> allModels = new HashMap<>();

            for (String source : sources.keySet()) {
                SourceConfig sourceConfig = sources.getOrDefault(source,null);
                if (sourceConfig != null){
                    List<ModelConfig> configs = sourceConfig.getModels();
                    if (configs!=null){
                        allModels.put(
                                source,
                                configs.stream().collect(
                                        Collectors.toMap(
                                                ModelConfig::getId,model -> model
                                        )
                                )
                        );
                    }
                }
            }

            this.allModels = allModels;
        }
    }

    public Map<String, SourceConfig> getAvailableModels(){
        return llmConfig.getSources();
    }

    public SourceConfig getModelConfigBySource(Source source) {
        Map<String,SourceConfig> configs = getAvailableModels();
        if (configs == null) throw new NotFoundException("No LLM models found. Please configure before execution");

        return configs.getOrDefault(source.getValue(),null);
    }

    public void isLlmSupported(@NotNull String source, @NotNull String type, @NotNull String model){
        Map<String,ModelConfig> models = allModels.getOrDefault(source,null);

        if (models==null) throw new NotFoundException("No model source exists");

        ModelConfig config = models.getOrDefault(model,null);

        if (config==null) throw new NotFoundException(String.format("Model %s not available under %s source",model,source));

        if (!config.getActive()) throw new NotActivatedException(String.format("Model %s not activated. Contact the service provider.",model));

        if (!config.getType().contains(type)) throw new NotAvailableException(String.format("Model %s does not supports %s type request. Available type: %s",model,type,config.getType()));
    }

    public List<String > getTypes() {
        return Arrays.stream(LlmCapability.values())
                .map(LlmCapability::getValue)
                .collect(Collectors.toList());
    }

    public ModelConfig getModelConfig(@NotNull String source, @NotNull String type, @NotNull String model){
        isLlmSupported(source, type, model);
        return getAvailableModels()
                .get(source)
                .getModels()
                .stream()
                .filter(filter -> filter.getId().equalsIgnoreCase(model))
                .toList()
                .getFirst();
    }
}
