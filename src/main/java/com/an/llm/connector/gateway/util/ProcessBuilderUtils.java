package com.an.llm.connector.gateway.util;

public class ProcessBuilderUtils {
    public static String generateProcessBuilderScript(String model, Integer context, Integer parallelExecution, Integer port){
        return String.format("cd ~/llama.cpp && ./build/bin/llama-server " +
                "-m models/%s " +
                "-c %s -np %s -t 8 -cb -ngl 999 " +
                "--cache-type-k q8_0 " +
                "--cache-type-v q8_0 " +
                "--host 0.0.0.0 --port %s",model,context,parallelExecution,port);
    }

    public static String generateProcessBuilderEmbedScript(String model, Integer context, Integer parallelExecution, Integer port){
        return String.format("cd ~/llama.cpp && ./build/bin/llama-server " +
                "-m models/%s " +
                "-c %s -np %s -t 8 -cb -ngl 999 " +
                "--host 0.0.0.0 --port %s " +
                "--embedding ",model,context,parallelExecution,port);
    }

    public static String generateProcessBuilderVlScript(String model, String mmProj,Integer context, Integer parallelExecution, Integer port){
        return String.format("cd ~/llama.cpp && ./build/bin/llama-server " +
                "-m models/%s " +
                "--mmproj models/%s " +
                "--cache-type-k q8_0 " +
                "--cache-type-v q8_0 " +
                "-cram 0 " +
                "-c %s -np %s -t 4 -cb " +
                "--host 0.0.0.0 --port %s ",model,mmProj,context,parallelExecution,port);
    }
}
