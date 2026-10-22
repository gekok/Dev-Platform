import api from '../../../shared/services/axios.js';

export async function listCards(boardId) {
  const response = await api.get(`/boards/${boardId}/cards`);
  return response.data;
}

export async function getCard(cardId) {
  const response = await api.get(`/cards/${cardId}`);
  return response.data;
}

export async function createCard(boardId, payload) {
  const response = await api.post(`/boards/${boardId}/cards`, payload);
  return response.data;
}

export async function moveCard(cardId, payload) {
  const response = await api.patch(`/cards/${cardId}/move`, payload);
  return response.data;
}

export async function assignCard(cardId, payload) {
  const response = await api.patch(`/cards/${cardId}/assignee`, payload);
  return response.data;
}

export async function listComments(cardId) {
  const response = await api.get(`/cards/${cardId}/comments`);
  return response.data;
}

export async function addComment(cardId, payload) {
  const response = await api.post(`/cards/${cardId}/comments`, payload);
  return response.data;
}
