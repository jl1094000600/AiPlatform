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
        <label>
          手机号或邮箱
          <input v-model="account" type="text" placeholder="name@example.com" />
        </label>
        <label>
          密码
          <input v-model="password" type="password" placeholder="请输入密码" />
        </label>
        <div class="form-row">
          <label class="check-line">
            <input type="checkbox" />
            保持登录状态
          </label>
          <a href="#">忘记密码？</a>
        </div>
        <button class="submit-btn" type="submit">{{ mode === 'login' ? '进入 Think Land' : '创建账号' }}</button>
      </form>
    </section>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const mode = ref('login')
const account = ref('')
const password = ref('')

function submit() {
  localStorage.setItem('consumer-auth', '1')
  router.push('/workspace')
}
</script>
