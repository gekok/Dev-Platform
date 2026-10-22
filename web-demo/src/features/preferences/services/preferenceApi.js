import api from '../../../shared/services/axios.js';

export async function listDefaultBoards() {
  const response = await api.get('/users/me/preferences/default-boards');
  return response.data;
}

export async function setDefaultBoard(projectId, boardId) {
  const response = await api.put(
    `/users/me/preferences/default-boards/${projectId}`,
    { boardId },
  );
  return response.data;
}

export async function clearDefaultBoard(projectId, expectedBoardId) {
  const response = await api.delete(
    `/users/me/preferences/default-boards/${projectId}`,
    expectedBoardId == null ? {} : { params: { expectedBoardId } },
  );
  return response.data;
}
