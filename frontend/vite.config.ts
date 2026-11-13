import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // Pre-bundle lazy-route dependencies before sign-in so first visiting a ticket/admin
  // page does not trigger an optimizer reload and clear the in-memory access token.
  optimizeDeps: { include: ['react', 'react-dom/client', 'react-router-dom', '@tanstack/react-query', 'axios', 'zustand', 'framer-motion', 'lucide-react', 'recharts', 'qrcode.react'] },
  server: { port: 5173, strictPort: true, proxy: { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true } } },
  preview: { port: 4173, proxy: { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true } } },
  test: { environment: 'jsdom', setupFiles: ['./src/test-setup.ts'], restoreMocks: true },
});
