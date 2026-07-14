package com.an.llm.connector.gateway.service.factory;

import com.an.llm.connector.gateway.exception.NotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreBeanFactory {
    private final Map<String, VectorStore> vectorStoreFactory;

    public VectorStore getVectorStore(@NonNull String id){
        String vectorStoreBeanId = "vector-"+id;
        if (!vectorStoreFactory.containsKey(vectorStoreBeanId)) throw new NotFoundException(String.format("No vector storage found for %s.",id));

        return vectorStoreFactory.get(vectorStoreBeanId);
    }
}
