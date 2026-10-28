package com.an.llm.connector.gateway.model;

import com.an.llm.connector.gateway.util.JsonUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiRequest {
    @NotBlank(message = "Agent name is mandatory.")
    @NotNull(message = "Agent name is mandatory.")
    private String agent;
    private String model;
    @NotNull(message = "Query is mandatory.")
    private String query;
    private List<MultipartFile> files;
    private boolean chatHistoryEnabled;
    private List<ChatHistory> chatHistory;
    //only for handling forms.
    private String chatHistoryForForm;

    public void setChatHistoryForForm(String chatHistoryForForm) {
        this.chatHistoryForForm = chatHistoryForForm;

        if (chatHistoryForForm != null && !chatHistoryForForm.isBlank()) {
            this.chatHistory = JsonUtils.deserializeString(
                    chatHistoryForForm,
                    new TypeReference<>() {}
            );
        }
    }
}
