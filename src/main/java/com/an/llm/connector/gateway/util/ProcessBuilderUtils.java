package com.an.llm.connector.gateway.util;

public class ProcessBuilderUtils {

    private static final String LLAMA_PATH = "~/llama.cpp";

    private static String getGpuArg(Boolean gpu) {
        return Boolean.TRUE.equals(gpu) ? "-ngl 999 " : "";
    }

    public static String generateProcessBuilderScript(String model, Integer context, Integer parallelExecution, Boolean gpu, Integer port) {
        return String.format(
                "cd %s && ./build/bin/llama-server " +
                        "-m models/%s " +
                        "-c %s -np %s -t 8 -cb %s" +
                        "--batch-size 3072 " +
                        "--ubatch-size 768 " +
                        "--cache-type-k q8_0 " +
                        "--cache-type-v q8_0 " +
                        "--flash-attn on " +
                        "--host 0.0.0.0 --port %s",
                LLAMA_PATH, model, context, parallelExecution, getGpuArg(gpu), port
        );
    }

    public static String generateProcessBuilderEmbedScript(String model, Integer context, Integer parallelExecution, Boolean gpu, Integer port) {
        return String.format(
                "cd %s && ./build/bin/llama-server " +
                        "-m models/%s " +
                        "-c %s -np %s -t 8 -cb %s" +
                        "--batch-size 3073 " +
                        "--ubatch-size 768 " +
                        "--cache-type-k q8_0 " +
                        "--cache-type-v q8_0 " +
                        "--flash-attn auto " +
                        "--host 0.0.0.0 --port %s " +
                        "--embedding",
                LLAMA_PATH, model, context, parallelExecution, getGpuArg(gpu), port
        );
    }

    public static String generateProcessBuilderVlScript(String model, String mmProj, Integer context, Integer parallelExecution, Boolean gpu, Integer port) {
        return String.format(
                "cd %s && ./build/bin/llama-server " +
                        "-m models/%s " +
                        "--mmproj models/%s " +
                        "-c %s -np %s -t 8 -cb %s" +
                        "--cache-type-k q8_0 " +
                        "--cache-type-v q8_0 " +
                        "--batch-size 3072 " +
                        "--ubatch-size 768 " +
                        "--flash-attn auto " +
                        "--cache-ram 0 " +
                        "--host 0.0.0.0 --port %s",
                LLAMA_PATH, model, mmProj, context, parallelExecution, getGpuArg(gpu), port
        );
    }
}