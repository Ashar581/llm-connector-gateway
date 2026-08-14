package com.an.llm.connector.gateway.util;

import com.an.llm.connector.gateway.exception.OperationFailedException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;

@Slf4j
public class JsonUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public <T> String serialize(T data){
        try{
            return objectMapper.writeValueAsString(data);
        }catch (Exception e){
            log.error("Unable to serialize object due to: {}",e.getMessage());
            throw new OperationFailedException("Serialization failed.");
        }
    }

    public static <T> String serializeClass(T data){
        if (data==null){
            log.error("Data received for serialization was null. Returning blank as default value.");
            return "";
        }
        try {
            return objectMapper.writeValueAsString(data);
        }catch (Exception e){
            log.error("Error while serializing the the class to String. ",e);
            return "";
        }
    }

    public static <T,T1> T1 deserializeClass(T data, Class<T1> clazz){
        if (data==null){
            log.error("Data received for deserializing was null. Returning null as default value");
            return null;
        }
        try{
            return objectMapper.convertValue(data, clazz);
        }catch (Exception e){
            log.error("Error deserializing class ",e);
            return null;
        }
    }

    public static <T,T1> T1 deserializeClass(T data, TypeReference<T1> typeReference){
        if (data==null){
            log.error("Data received for deserializing was null. Returning null as default value");
            return null;
        }
        try{
            return objectMapper.convertValue(data, typeReference);
        }catch (Exception e){
            log.error("Error deserializing class ",e);
            return null;
        }
    }

    public static <T,T1> T1 deserializeString(String data, Class<T1> clazz){
        if (data==null){
            log.error("Data received for deserializing was null. Returning null as default value");
            return null;
        }
        try{
            return objectMapper.readValue(data, clazz);
        }catch (Exception e){
            log.error("Error deserializing class ",e);
            return null;
        }
    }

    public static <T,T1> T1 deserializeString(String data, TypeReference<T1> typeReference){
        if (data==null){
            log.error("Data received for deserializing was null. Returning null as default value");
            return null;
        }
        try{
            return objectMapper.readValue(data, typeReference);
        }catch (Exception e){
            log.error("Error deserializing class ",e);
            return null;
        }
    }

    public <T> T deserialize(InputStream from, Class<T> to){
        try{
            return objectMapper.readValue(from,to);
        } catch (Exception e){
            log.error("Unable to deserialize object: {}", e.getMessage());
            throw new OperationFailedException("Deserialization failure");
        }
    }
}
