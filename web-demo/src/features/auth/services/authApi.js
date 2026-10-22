import api from '../../../shared/services/axios.js';

export async function login(credentials) {
  const response = await api.post('/auth/login', credentials);
  return response.data;
}

export async function getCurrentUser() {
  const response = await api.get('/users/me');
  return response.data;
}
