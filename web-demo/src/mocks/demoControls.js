import { mockRepository } from './data/mockRepository.js';

export function resetDemoData() {
  return mockRepository.reset();
}
