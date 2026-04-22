package com.an.llm.connector.gateway.enums;

import com.an.llm.connector.gateway.exception.NotFoundException;

public enum Source {
    FREE("free"),
    PAID("paid");

    private final String id;

    Source(String id){
        this.id = id;
    }

    public String getValue(){
        return this.id;
    }

    public static Source getFromValue(String value){
        for (Source source : Source.values()){
            if (source.getValue().equalsIgnoreCase(value)){
                return source;
            }
        }
        throw new NotFoundException("Invalid source type request. Can either be Free or Paid.");
    }
}
