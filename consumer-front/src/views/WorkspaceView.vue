<template>
  <main class="workspace-page">
    <aside class="workspace-sidebar">
      <RouterLink class="brand compact" to="/">
        <img class="brand-logo" src="/think-land-logo.svg" alt="Think Land logo" />
        <strong>Think Land</strong>
      </RouterLink>
      <button class="side-link active">创意工作台</button>
      <button class="side-link">我的 PRD</button>
      <button class="side-link">流程图</button>
      <button class="side-link">任务计划</button>
      <button class="side-link" @click="logout">退出登录</button>
    </aside>

    <section class="workspace-main">
      <header class="workspace-header">
        <div>
          <p class="eyebrow">个人使用页面</p>
          <h1>今天想把哪个想法变成产品？</h1>
        </div>
        <button class="primary-btn small" @click="generate">生成方案</button>
      </header>

      <section class="composer-panel">
        <textarea v-model="idea" placeholder="例如：我想做一个帮助上班族管理健康饮食的小程序，需要能生成计划、提醒打卡、记录反馈。"></textarea>
        <div class="composer-actions">
          <span>AI 会先生成 PRD，再给出流程图和任务拆解。</span>
          <button @click="generate">开始生成</button>
        </div>
      </section>

      <section class="workspace-grid">
        <article class="result-panel prd-panel">
          <div class="panel-title">
            <span>PRD 草稿</span>
            <b>{{ loading ? '生成中' : '已就绪' }}</b>
          </div>
          <ul>
            <li v-for="item in prd" :key="item">{{ item }}</li>
          </ul>
        </article>

        <article class="result-panel flow-panel">
          <div class="panel-title">
            <span>业务流程图</span>
            <b>预览</b>
          </div>
          <div class="workspace-flow">
            <i>用户输入</i>
            <b></b>
            <i>需求理解</i>
            <b></b>
            <i>PRD 生成</i>
            <b></b>
            <i>流程拆解</i>
          </div>
        </article>

        <article class="result-panel tasks-panel">
          <div class="panel-title">
            <span>下一步任务</span>
            <b>4 项</b>
          </div>
          <div class="task-list">
            <p>确认目标用户和核心场景</p>
            <p>补充登录、支付、通知等基础能力</p>
            <p>将流程图转为开发任务</p>
            <p>保存为我的项目</p>
          </div>
        </article>
      </section>
    </section>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const idea = ref('我想做一个帮助个人创作者把想法整理成 PRD 和流程图的 AI 工作台。')
const loading = ref(false)
const prd = ref([
  '目标用户：个人创作者、独立开发者、小团队负责人',
  '核心问题：想法分散，难以快速沉淀为可执行方案',
  '核心功能：一句话生成 PRD、自动生成流程图、任务拆解',
  '验收标准：用户可在 3 分钟内得到第一版产品计划'
])

function generate() {
  loading.value = true
  window.setTimeout(() => {
    prd.value = [
      `产品想法：${idea.value.slice(0, 34)}${idea.value.length > 34 ? '...' : ''}`,
      '目标用户：有明确想法但缺少产品表达经验的用户',
      '核心功能：需求归纳、PRD 生成、流程图生成、任务拆解',
      '建议下一步：补充目标用户、使用场景和商业约束'
    ]
    loading.value = false
  }, 800)
}

function logout() {
  localStorage.removeItem('consumer-auth')
  router.push('/')
}
</script>
