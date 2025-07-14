import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/upload-audio': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/submit-text': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/submit-sheet': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }, // ← ✅ ここにカンマが必要！
    },
  },
})
