import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  // 让 Vite 能识别并编译 .vue 单文件组件。
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    clearMocks: true,
  },
  server: {
    port: 5173,
    // 端口占用就报错，不自动换端口，方便固定地址联调。
    strictPort: true,
    proxy: {
      '/api': {
        // 浏览器请求 5173 的 /api，Vite 再转发给 8080 的后端。
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 不删除 /api 前缀：后端路径本身就是 /api/health。
      },
    },
  },
})
