<template>
  <main class="auth-page">
    <RouterLink class="auth-brand" to="/">
      <img class="brand-logo" src="/think-land-logo.svg" alt="Think Land logo" />
      <strong>Think Land</strong>
    </RouterLink>

    <section class="auth-showcase">
      <article>
        <span>01</span>
        <h2>灵感型</h2>
        <p>适合创作、灵感记录和 AI 陪伴式产品规划。</p>
      </article>
      <article>
        <span>02</span>
        <h2>效率型</h2>
        <p>适合把个人任务、助手和知识库聚合为每日工作台。</p>
      </article>
      <article>
        <span>03</span>
        <h2>会员型</h2>
        <p>适合持续保存项目、复用模板和享受更高额度服务。</p>
      </article>
    </section>

    <section class="auth-content">
      <div class="auth-copy">
        <p class="pill">注册 / 登录</p>
        <h1>先让用户轻松进来，再把 AI 能力慢慢展开</h1>
        <p>登录后进入个人工作台，你可以输入一句话产品想法，查看 PRD、流程图和任务拆解的生成效果。</p>
      </div>

      <form class="auth-card" @submit.prevent="submit">
        <div class="auth-tabs">
          <button type="button" :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
          <button type="button" :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
        </div>
        <div v-if="mode === 'register'" class="form-notice" role="status">
          当前版本暂不开放自助注册，请联系平台管理员为你开通账号并加入租户。
        </div>
        <label v-if="mode === 'login'">
          用户名
          <input v-model.trim="account" type="text" autocomplete="username" placeholder="请输入用户名" :disabled="loading" />
        </label>
        <label v-if="mode === 'login'">
          密码
          <input v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" :disabled="loading" />
        </label>
        <div v-if="mode === 'login'" class="form-row">
          <span>账号由管理员统一开通</span>
          <span>忘记密码请联系管理员</span>
        </div>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="submit-btn" type="submit" :disabled="loading || mode === 'register'">
          {{ mode === 'login' ? (loading ? '正在登录...' : '进入 Think Land') : '请联系管理员开通' }}
        </button>
      </form>
    </section>
  </main>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi, saveAuth } from '@/api'

const router = useRouter()
const route = useRoute()
const mode = ref('login')
const account = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

watch(mode, () => {
  error.value = ''
})

async function submit() {
  if (mode.value === 'register') return
  if (!account.value || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  error.value = ''
  try {
    const login = await authApi.login({ username: account.value, password: password.value })
    saveAuth(login)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/workspace'
    await router.replace(redirect)
  } catch (requestError) {
    error.value = requestError.message || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>
