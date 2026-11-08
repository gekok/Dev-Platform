import api from '../../../shared/services/axios.js';
import {
  listAllApiPages,
  unwrapApiResponse,
} from '../../../shared/services/apiResponses.js';

function normalizeColumn(column) {
  return column && typeof column === 'object' ? { ...column } : column;
}

function normalizeBoard(board) {
  if (!board || typeof board !== 'object') return board;
  const boardKey = board.boardKey ?? board.key;
  const normalized = {
    ...board,
    boardKey,
    key: typeof boardKey === 'string' ? boardKey.toLowerCase() : boardKey,
    role: board.role ?? board.myRole,
  };
  if (Array.isArray(board.columns)) {
    normalized.columns = board.columns.map(normalizeColumn);
  }
  return normalized;
}

export async function listProjectBoards(projectId) {
  const page = await listAllApiPages(`/projects/${projectId}/boards`, {
    sort: 'createdAt,desc',
  });
  return { ...page, items: page.items.map(normalizeBoard) };
}

export async function createProjectBoard(projectId, payload) {
  const { key, ...request } = payload;
  const response = await api.post(`/projects/${projectId}/boards`, {
    ...request,
    boardKey: payload.boardKey ?? key,
  });
  return normalizeBoard(unwrapApiResponse(response.data));
}

export async function getBoard(boardId) {
  const response = await api.get(`/boards/${boardId}`);
  return normalizeBoard(unwrapApiResponse(response.data));
}

export async function addColumn(boardId, payload) {
  const response = await api.post(`/boards/${boardId}/columns`, payload);
  const data = unwrapApiResponse(response.data);
  if (!data || typeof data !== 'object') return data;
  return {
    ...data,
    column: normalizeColumn(data.column),
    columns: Array.isArray(data.columns) ? data.columns.map(normalizeColumn) : data.columns,
  };
}

export async function reorderColumns(boardId, columnIds) {
  const response = await api.put(`/boards/${boardId}/columns/order`, { columnIds });
  const data = unwrapApiResponse(response.data);
  return Array.isArray(data) ? data.map(normalizeColumn) : data;
}
