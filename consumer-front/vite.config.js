import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from '../front/node_modules/vite/dist/node/index.js'
import vue from '../front/node_modules/@vitejs/plugin-vue/dist/index.mjs'

const rootDir = dirname(fileURLToPath(import.meta.url))
const sharedModules = resolve(rootDir, '../front/node_modules')

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(rootDir, 'src'),
      vue: resolve(sharedModules, 'vue/dist/vue.runtime.esm-bundler.js'),
      'vue-router': resolve(sharedModules, 'vue-router/dist/vue-router.mjs')
    }
  },
  server: {
    port: 3100,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
