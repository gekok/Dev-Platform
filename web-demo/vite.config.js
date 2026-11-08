import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import {
  resolveApiProxyTarget,
  resolveRuntimeConfig,
} from './src/shared/config/runtimeConfig.js';

export default defineConfig(({ mode, command }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const runtime = resolveRuntimeConfig(env);
  const proxyTarget = resolveApiProxyTarget(env, { mode: runtime.mode, command });
  const apiProxy = proxyTarget ? {
    '/api': {
      target: proxyTarget,
      changeOrigin: true,
    },
  } : undefined;

  return {
    plugins: [react()],
    publicDir: runtime.mode === 'demo' ? 'public' : false,
    server: {
      port: 5173,
      strictPort: true,
      proxy: apiProxy,
    },
    preview: { proxy: apiProxy },
  };
});
