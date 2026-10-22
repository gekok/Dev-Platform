import api from '../../../shared/services/axios.js';

export async function listProjectBoards(projectId) {
  const response = await api.get(`/projects/${projectId}/boards`);
  return response.data;
}

export async function createProjectBoard(projectId, payload) {
  const response = await api.post(`/projects/${projectId}/boards`, payload);
  return response.data;
}

export async function getBoard(boardId) {
  const response = await api.get(`/boards/${boardId}`);
  return response.data;
}

export async function addColumn(boardId, payload) {
  const response = await api.post(`/boards/${boardId}/columns`, payload);
  return response.data;
}

export async function reorderColumns(boardId, orderedColumnIds) {
  const response = await api.patch(`/boards/${boardId}/columns/order`, {
    orderedColumnIds,
  });
  return response.data;
}
