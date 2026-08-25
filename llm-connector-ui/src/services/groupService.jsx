import apiSvc from "./apiService";

// ── Group endpoints — api/llm/v1/groups ────────────────────────────────────

export const getGroup = async (code, signal) => {
	const response = await apiSvc.get("v1/groups", { params: { code }, signal });
	return response.data.data;
};

export const getAllGroups = async (signal) => {
	const response = await apiSvc.get("v1/groups/all", { signal });
	return response.data.data;
};

export const getGroupsByCodes = async (codes, signal) => {
	const response = await apiSvc.get("v1/groups/by-codes", {
		params: { codes: Array.isArray(codes) ? codes.join(",") : codes },
		signal,
	});
	return response.data.data;
};

export const addGroup = async (dto) => {
	const response = await apiSvc.post("v1/groups", dto);
	return response.data.data;
};

export const updateGroup = async (dto) => {
	const response = await apiSvc.put("v1/groups", dto);
	return response.data.data;
};
