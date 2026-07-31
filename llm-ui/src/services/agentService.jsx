import apiSvc from "./apiService";

export const createAgent = async (payload) => {
	const fd = new FormData();

	const cleanedPayload = { ...payload };

	if (!cleanedPayload.maxTokens) {
		delete cleanedPayload.maxTokens;
	}

	Object.keys(cleanedPayload).forEach((key) => {
		let value = cleanedPayload[key];

		// Convert objects/arrays before appending
		if (
			typeof value === "object" &&
			value !== null
		) {
			value = JSON.stringify(value);
		}

		fd.append(key, value);
	});

	const response = await apiSvc.post(
		"/v1/agent-config",
		fd
	);

	return response.data;
};

export const getAgents = async (signal) => {
	const response = await apiSvc.get(
		"v1/agent-config",
		{ signal }
	);

	return response.data.data;
};

export const getModels = async (signal) => {
	const response = await apiSvc.get("v1/config/model", { signal })
	return response.data.data;
}

export const getAgentTypes = async (signal) => {
	const response = await apiSvc.get(
		"v1/config/types",
		{ signal }
	);

	return response.data.data;
};

export const createNewAgent = async (
	payload,
	files = []
) => {
	const fd = new FormData();

	const data = { ...payload };

	if (!data.maxTokens) {
		delete data.maxTokens;
	}

	Object.entries(data).forEach(
		([key, value]) => {
			if (
				typeof value === "object" &&
				value !== null
			) {
				fd.append(
					key,
					JSON.stringify(value)
				);
			} else {
				fd.append(key, value);
			}
		}
	);

	files.forEach((file) =>
		fd.append("agentFiles", file)
	);

	const response = await apiSvc.post(
		"v1/agent-config",
		fd,
		{
			headers: {
				"Content-Type":
					"multipart/form-data",
			},
		}
	);

	return response.data;
};

export const updateAgent = async (
	agentId,
	payload
) => {
	const response = await apiSvc.put(
		`v1/agent-config/${agentId}`,
		payload
	);

	return response.data;
};

export const deleteAgent = async (agentName) => {
	const response = await apiSvc.delete(`/v1/agent-config/${agentName}`)
	return response.data;
}