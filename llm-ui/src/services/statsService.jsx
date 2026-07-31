import apiSvc from "./apiService";

export const getStats = async (signal) => {
    const response = await apiSvc.get('v1/stats/current-day', { signal });
    return response.data;
}

export const getModelStats = async (params, signal) => {
    const response = await apiSvc.get('v1/stats/filter', {
        params,
        signal
    });

    return response.data;
};