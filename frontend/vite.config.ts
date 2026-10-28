import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the React app calls /api and /uploads on its own origin,
// and Vite forwards them to the Spring Boot server.
// Default port 8080. If something else already uses 8080 (Jenkins, Tomcat...), run the API on
// another port and start Vite with the same number, e.g. API_PORT=8081 npm run dev
const target = `http://localhost:${process.env.API_PORT ?? '8080'}`;

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': target,
      '/uploads': target,
    },
  },
});
