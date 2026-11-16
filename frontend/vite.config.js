import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': resolve(__dirname, 'src') }
  },
  server: {
    port: 5174,
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 后端 CorsConfig 只放行 5173，本地样板跑在 5174 上会 403；这里去掉 Origin 走服务端转发
        configure: proxy => {
          proxy.on('proxyReq', proxyReq => proxyReq.removeHeader('origin'))
        }
      }
    }
  },
  build: { outDir: 'dist', assetsDir: 'assets', sourcemap: false }
})
