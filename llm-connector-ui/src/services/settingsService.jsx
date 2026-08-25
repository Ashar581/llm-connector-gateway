import apiSvc from "./apiService";

// ── Settings (route-access) endpoints — api/llm/v1/settings ────────────────

export const getAllSettings = async (signal) => {
    const response = await apiSvc.get("v1/settings/all", { signal });
    return response.data.data;
};

export const addSettingsBulk = async (dtoList) => {
    const response = await apiSvc.post("v1/settings/bulk/add", dtoList);
    return response.data.data;
};

export const addSetting = async (dto) => {
    const response = await apiSvc.post("v1/settings/add", dto);
    return response.data.data;
};

export const updateSetting = async (dto) => {
    const response = await apiSvc.put("v1/settings/update", dto);
    return response.data.data;
};

export const deleteSetting = async (routePath) => {
    const response = await apiSvc.delete("v1/settings/delete", { params: { route: routePath } });
    return response.data.data;
};
