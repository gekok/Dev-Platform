import api from '../../../shared/services/axios.js';

export async function listProjects() {
  const response = await api.get('/projects');
  return response.data;
}

export async function getProject(projectId) {
  const response = await api.get(`/projects/${projectId}`);
  return response.data;
}

export async function createProject(payload) {
  const response = await api.post('/projects', payload);
  return response.data;
}

export async function listProjectMembers(projectId) {
  const response = await api.get(`/projects/${projectId}/members`);
  return response.data;
}
