const SUPPORTED_MODES = new Set(['demo', 'backend']);

export async function startRuntime({ mode, startDemoWorker, renderApp }) {
  if (!SUPPORTED_MODES.has(mode)) {
    throw new Error('Runtime mode must be resolved before application startup.');
  }

  if (mode === 'demo') {
    await startDemoWorker();
  }

  renderApp();
}
