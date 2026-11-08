import api from '../../../shared/services/axios.js';
import {
  createCardApiRoutes,
  requireCardListContract,
} from '../cardApiRoutes.js';

function routesForCurrentMode() {
  return createCardApiRoutes(import.meta.env.VITE_APP_MODE);
}

export async function listCards(projectId, boardId) {
  requireCardListContract(import.meta.env.VITE_APP_MODE);
  const response = await api.get(routesForCurrentMode().cards(projectId, boardId));
  return response.data;
}

export async function getCard(projectId, cardId) {
  const response = await api.get(routesForCurrentMode().card(projectId, cardId));
  return response.data;
}

export async function createCard(projectId, boardId, payload) {
  const response = await api.post(
    routesForCurrentMode().createCard(projectId, boardId),
    payload,
  );
  return response.data;
}

export async function moveCard(projectId, cardId, payload) {
  const response = await api.patch(
    routesForCurrentMode().moveCard(projectId, cardId),
    payload,
  );
  return response.data;
}

export async function assignCard(projectId, cardId, payload) {
  const response = await api.patch(
    routesForCurrentMode().assignCard(projectId, cardId),
    payload,
  );
  return response.data;
}

export async function listComments(projectId, cardId) {
  const response = await api.get(routesForCurrentMode().comments(projectId, cardId));
  return response.data;
}

export async function addComment(projectId, cardId, payload) {
  const response = await api.post(
    routesForCurrentMode().comments(projectId, cardId),
    payload,
  );
  return response.data;
}
