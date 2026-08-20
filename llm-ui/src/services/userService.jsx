import apiSvc from "./apiService";

// ── User endpoints — api/llm/v1/users ──────────────────────────────────────

export const getUser = async (id, signal) => {
	const response = await apiSvc.get("v1/users", { params: { id }, signal });
	return response.data.data;
};

export const getAllUsers = async (active, signal) => {
	const response = await apiSvc.get("v1/users/all", {
		params: active === undefined ? {} : { active },
		signal,
	});
	return response.data.data;
};

export const addUser = async (dto) => {
	const response = await apiSvc.post("v1/users/add", dto);
	return response.data.data;
};

export const updateUser = async (dto) => {
	const response = await apiSvc.put("v1/users/update", dto);
	return response.data.data;
};
