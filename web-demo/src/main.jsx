import React from 'react';
import ReactDOM from 'react-dom/client';
import 'bootstrap/dist/css/bootstrap.min.css';
import './index.css';
import App from './app/App.jsx';
import AppProviders from './app/providers/AppProviders.jsx';

const root = ReactDOM.createRoot(document.getElementById('root'));

async function bootstrap() {
  const { worker } = await import('./mocks/browser.js');
  await worker.start({ onUnhandledRequest: 'bypass' });
  root.render(
    <React.StrictMode>
      <AppProviders>
        <App />
      </AppProviders>
    </React.StrictMode>,
  );
}

bootstrap().catch(() => {
  root.render(
    <main className="route-state" role="alert">
      The demo API could not start. Reload the page to try again.
    </main>,
  );
});
