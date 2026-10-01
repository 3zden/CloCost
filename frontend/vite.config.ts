import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // Dev only: same-origin proxy so the Spring backend needs no CORS config. nginx does this in prod.
  server: { proxy: { '/api': 'http://localhost:8080' } },
});
