import React from 'react';
import ReactDOM from 'react-dom/client';
import 'bootstrap/dist/css/bootstrap.min.css';
import './index.css';
import App from './app/App.jsx';
import AppProviders from './app/providers/AppProviders.jsx';
import { resolveRuntimeConfig } from './shared/config/runtimeConfig.js';
import { startRuntime } from './shared/config/runtimeStartup.js';
import { configureApiBaseUrl } from './shared/services/axios.js';

const root = ReactDOM.createRoot(document.getElementById('root'));

function renderApp() {
  root.render(
    <React.StrictMode>
      <AppProviders>
        <App />
      </AppProviders>
    </React.StrictMode>,
  );
}

function renderStartupError(title, message) {
  root.render(
    <main className="route-state" role="alert" aria-live="assertive">
      <div>
        <h1>{title}</h1>
        <p>{message}</p>
      </div>
    </main>,
  );
}

const startDemoWorker = import.meta.env.VITE_APP_MODE === 'demo'
  ? async () => {
    const { worker } = await import('./mocks/browser.js');
    await worker.start({ onUnhandledRequest: 'error' });
  }
  : async () => {};

async function bootstrap() {
  let runtime;
  try {
    runtime = resolveRuntimeConfig(import.meta.env);
    configureApiBaseUrl(runtime.apiBaseUrl);
  } catch (error) {
    renderStartupError('Web client configuration error', error.message);
    return;
  }

  try {
    await startRuntime({ mode: runtime.mode, startDemoWorker, renderApp });
  } catch {
    if (runtime.mode === 'demo') {
      renderStartupError(
        'Demo API could not start',
        'The Demo request worker failed to start. Check the browser console and reload.',
      );
    } else {
      renderStartupError(
        'Web client could not start',
        'The application failed during startup. Check the browser console and reload.',
      );
    }
  }
}

bootstrap();
