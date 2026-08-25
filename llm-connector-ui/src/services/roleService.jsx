import apiSvc from "./apiService";

// ── Role endpoints — api/llm/v1/roles ──────────────────────────────────────

export const getRole = async (code, signal) => {
	const response = await apiSvc.get("v1/roles", { params: { code }, signal });
	return response.data.data;
};

export const getAllRoles = async (signal) => {
	const response = await apiSvc.get("v1/roles/all", { signal });
	return response.data.data;
};

export const getRolesByCodes = async (codes, signal) => {
	const response = await apiSvc.get("v1/roles/by-codes", {
		params: { codes: Array.isArray(codes) ? codes.join(",") : codes },
		signal,
	});
	return response.data.data;
};

export const addRole = async (dto) => {
	const response = await apiSvc.post("v1/roles", dto);
	return response.data.data;
};

export const addRolesBulk = async (dtoList) => {
	const response = await apiSvc.post("v1/roles/bulk", dtoList);
	return response.data.data;
};

export const updateRole = async (dto) => {
	const response = await apiSvc.put("v1/roles", dto);
	return response.data.data;
};
