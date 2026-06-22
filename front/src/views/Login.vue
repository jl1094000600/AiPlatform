<template>
  <main class="login-page">
    <section class="login-panel-left" aria-label="AI 平台品牌信息">
      <canvas ref="brandCanvas" class="brand-canvas" aria-hidden="true" />
      <div class="brand-content">
        <div class="brand-hero">
          <p class="brand-title">AI 平台</p>
        </div>
      </div>
    </section>

    <section class="login-panel-right">
      <div class="login-container">
        <div class="brand-lockup">
          <el-icon class="brand-mark" :size="34"><Box /></el-icon>
          <span>AI 平台</span>
        </div>

        <header class="login-header">
          <h1 class="login-title">欢迎回来</h1>
          <p class="login-subtitle">登录以访问您的 AI 运维控制台</p>
        </header>

        <el-form :model="form" class="login-form" @submit.prevent="handleLogin">
          <div class="form-item">
            <label class="form-label" for="login-username">用户名</label>
            <el-input
              id="login-username"
              v-model="form.username"
              class="custom-input"
              placeholder="请输入用户名"
              size="large"
              :prefix-icon="User"
              autocomplete="username"
            />
          </div>

          <div class="form-item">
            <label class="form-label" for="login-password">密码</label>
            <el-input
              id="login-password"
              v-model="form.password"
              class="custom-input"
              type="password"
              placeholder="请输入密码"
              size="large"
              show-password
              :prefix-icon="Lock"
              autocomplete="current-password"
              @keyup.enter="handleLogin"
            />
          </div>

          <div class="form-options">
            <el-checkbox v-model="rememberMe">记住我</el-checkbox>
          </div>

          <el-button
            type="primary"
            size="large"
            native-type="submit"
            class="login-btn"
            :loading="loading"
          >
            {{ loading ? '登录中...' : '登录' }}
          </el-button>
        </el-form>
      </div>
    </section>
  </main>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Box, Lock, User } from '@element-plus/icons-vue'
import api from '../api'

const router = useRouter()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const rememberMe = ref(false)
const brandCanvas = ref(null)

let animationFrame = 0
let resizeObserver = null
let prefersReducedMotion = false

const resizeCanvas = () => {
  const canvas = brandCanvas.value
  if (!canvas) return
  const bounds = canvas.getBoundingClientRect()
  const scale = Math.min(window.devicePixelRatio || 1, 2)
  canvas.width = Math.max(1, Math.floor(bounds.width * scale))
  canvas.height = Math.max(1, Math.floor(bounds.height * scale))
}

const drawBrandBackground = (time = 0) => {
  const canvas = brandCanvas.value
  if (!canvas) return

  const context = canvas.getContext('2d')
  const scale = Math.min(window.devicePixelRatio || 1, 2)
  const width = canvas.width / scale
  const height = canvas.height / scale
  const phase = time / 1000
  context.setTransform(scale, 0, 0, scale, 0, 0)
  context.clearRect(0, 0, width, height)
  context.fillStyle = '#071b36'
  context.fillRect(0, 0, width, height)

  const streamCount = 42
  for (let index = 0; index < streamCount; index += 1) {
    const seed = (index * 53) % 97
    const x = width * (0.12 + ((index * 29) % 84) / 100)
    const speed = 0.1 + (seed % 7) * 0.018
    const offset = (phase * speed + seed * 0.037) % 1
    const y = height * (0.44 + offset * 0.48)
    const trail = 14 + (seed % 5) * 7

    context.fillStyle = `rgba(37, 99, 235, ${0.12 + (seed % 4) * 0.04})`
    context.fillRect(x, y - trail, 1, trail)
    context.fillStyle = `rgba(96, 165, 250, ${0.5 + (seed % 3) * 0.12})`
    context.fillRect(x - 1, y - 1, 3, 3)
  }

  const signalCount = 14
  for (let index = 0; index < signalCount; index += 1) {
    const seed = (index * 31) % 71
    const x = width * (0.28 + index * 0.047)
    const travel = (phase * (0.055 + (seed % 4) * 0.012) + seed * 0.02) % 1
    const y = height * (0.56 + travel * 0.34)
    context.fillStyle = 'rgba(59, 130, 246, 0.16)'
    context.fillRect(x, y - 22, 1, 22)
    context.fillStyle = '#3b82f6'
    context.fillRect(x - 2, y - 2, 4, 4)
  }

  if (!prefersReducedMotion) {
    animationFrame = window.requestAnimationFrame(drawBrandBackground)
  }
}

onMounted(() => {
  prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  resizeCanvas()
  resizeObserver = new ResizeObserver(resizeCanvas)
  resizeObserver.observe(brandCanvas.value)
  drawBrandBackground()
})

onBeforeUnmount(() => {
  window.cancelAnimationFrame(animationFrame)
  resizeObserver?.disconnect()
})

const handleLogin = async () => {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  loading.value = true
  try {
    const res = await api.login(form)
    if (res.data.code === 200) {
      localStorage.setItem('token', res.data.data.token)
      localStorage.setItem('user', JSON.stringify(res.data.data))
      ElMessage.success({ message: '登录成功，欢迎回来！', duration: 1500 })
      router.push('/dashboard')
    } else {
      ElMessage.error(res.data.message || '登录失败')
    }
  } catch (e) {
    ElMessage.error('登录失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: clamp(520px, 42.7vw, 720px) minmax(0, 1fr);
  min-height: 100vh;
  background: #ffffff;
}

.login-panel-left {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  background-color: #071b36;
}

.brand-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.brand-content {
  position: relative;
  z-index: 1;
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  justify-content: center;
  padding: clamp(64px, 8vw, 132px) clamp(56px, 5.7vw, 96px);
  padding-bottom: clamp(180px, 24vh, 300px);
  color: #ffffff;
}

.brand-hero {
  max-width: 100%;
}

.brand-title {
  margin: 0;
  color: #ffffff;
  font-size: clamp(52px, 5vw, 78px);
  font-weight: 750;
  line-height: 1.08;
}

.login-panel-right {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  padding: 56px 48px;
  background: #ffffff;
}

.login-container {
  width: min(100%, 512px);
  padding: 0 22px;
  animation: form-enter 320ms var(--ease-out) both;
}

.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 46px;
  color: #111827;
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
}

.brand-mark {
  color: #2563eb;
}

.login-header {
  margin-bottom: 46px;
}

.login-title {
  margin: 0;
  color: #111827;
  font-size: 40px;
  font-weight: 700;
  line-height: 1.2;
}

.login-subtitle {
  margin: 14px 0 0;
  color: #64748b;
  font-size: 16px;
  line-height: 1.6;
}

.login-form {
  display: grid;
  gap: 28px;
}

.form-item {
  display: grid;
  gap: 10px;
}

.form-label {
  color: #1f2937;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
}

.custom-input :deep(.el-input__wrapper) {
  min-height: 58px;
  padding: 0 16px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: none;
  transition: border-color 150ms ease, box-shadow 150ms ease;
}

.custom-input :deep(.el-input__wrapper:hover) {
  border-color: #93c5fd;
}

.custom-input :deep(.el-input__wrapper.is-focus) {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.14);
}

.custom-input :deep(.el-input__inner) {
  color: #111827;
  font-size: 16px;
}

.custom-input :deep(.el-input__inner::placeholder) {
  color: #94a3b8;
}

.custom-input :deep(.el-input__prefix-inner),
.custom-input :deep(.el-input__suffix-inner) {
  color: #64748b;
}

.form-options {
  display: flex;
  align-items: center;
  min-height: 20px;
  margin-top: -2px;
}

.form-options :deep(.el-checkbox__label) {
  color: #475569;
  font-size: 14px;
}

.form-options :deep(.el-checkbox__inner) {
  width: 18px;
  height: 18px;
  border-color: #cbd5e1;
  border-radius: 4px;
}

.form-options :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  border-color: #2563eb;
  background: #2563eb;
}

.login-btn {
  width: 100%;
  height: 56px;
  margin-top: 8px;
  border-radius: 8px;
  background: #2563eb;
  border-color: #2563eb;
  box-shadow: none;
  color: #ffffff;
  font-size: 17px;
  font-weight: 600;
}

.login-btn:hover:not(.is-loading),
.login-btn:focus-visible:not(.is-loading) {
  background: #1d4ed8;
  border-color: #1d4ed8;
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.2);
}

.login-btn:focus-visible {
  outline: 3px solid rgba(37, 99, 235, 0.22);
  outline-offset: 3px;
}

@keyframes form-enter {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1320px) {
  .login-page {
    grid-template-columns: clamp(460px, 41vw, 570px) minmax(0, 1fr);
  }
}

@media (max-width: 1100px) {
  .login-page {
    grid-template-columns: clamp(440px, 40vw, 520px) minmax(0, 1fr);
  }

  .login-panel-right {
    padding: 48px 32px;
  }

  .login-container {
    padding: 0;
  }

  .brand-content {
    padding-inline: 48px;
  }

}

@media (max-width: 960px) {
  .login-page {
    display: block;
  }

  .login-panel-left {
    display: none;
  }

  .login-panel-right {
    min-height: 100vh;
    padding: 40px 24px;
  }

  .login-container {
    width: min(100%, 440px);
  }
}

@media (max-width: 480px) {
  .login-panel-right {
    padding: 32px 20px;
  }

  .brand-lockup {
    margin-bottom: 34px;
    font-size: 21px;
  }

  .login-header {
    margin-bottom: 36px;
  }

  .login-title {
    font-size: 32px;
  }

  .login-form {
    gap: 24px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-container {
    animation: none;
  }
}
</style>
