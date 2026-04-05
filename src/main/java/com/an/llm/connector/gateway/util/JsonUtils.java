package com.an.llm.connector.gateway.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
}
