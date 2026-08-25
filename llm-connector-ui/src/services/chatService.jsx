// src/services/chatService.jsx

import apiSvc from "./apiService";

const formatResponse = (answer) => {
    if (typeof answer === "object") {
        return (
            "```json\n" +
            JSON.stringify(answer, null, 2) +
            "\n```"
        );
    }

    if (typeof answer === "string") {
        try {
            const parsed = JSON.parse(answer);

            if (typeof parsed === "object") {
                return (
                    "```json\n" +
                    JSON.stringify(parsed, null, 2) +
                    "\n```"
                );
            }
        } catch {
            return answer;
        }
    }

    return answer;
};

export const sendMessage = async (payload) => {
    const response = await apiSvc.post(
        "/v2/ask",
        payload
    );

    return formatResponse(
        response.data.data
    );
};
export const sendEmbedding = async (payload) => {
    const response = await apiSvc.post('v2/embed', payload);
    return formatResponse(response.data.data)
}

export const sendFileRequest = async (
    endpoint,
    formData
) => {
    const response = await apiSvc.post(
        endpoint,
        formData,
        {
            headers: {
                "Content-Type":
                    "multipart/form-data",
            },
        }
    );

    return {
        raw: response.data.data,
        formatted: formatResponse(
            response.data.data
        ),
    };
};