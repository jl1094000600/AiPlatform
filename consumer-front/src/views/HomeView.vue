<template>
  <main class="site-page home-page">
    <nav class="site-nav">
      <RouterLink class="brand" to="/">
        <img class="brand-logo" src="/think-land-logo.svg" alt="Think Land logo" />
        <strong>Think Land</strong>
      </RouterLink>
      <div class="nav-links">
        <a href="#product">产品</a>
        <a href="#motion">演示</a>
        <a href="#notice">说明</a>
      </div>
      <RouterLink class="nav-cta" to="/login">开始使用</RouterLink>
    </nav>

    <section class="hero">
      <div class="hero-scene" aria-hidden="true">
        <div class="scene-grid"></div>
        <div class="prd-window">
          <div class="window-top">
            <span></span><span></span><span></span>
          </div>
          <p class="voice-line">“帮我做一个面向个人创作者的 AI 项目助手”</p>
          <div class="typing-card">
            <span>正在生成 PRD</span>
            <strong>产品目标 / 用户旅程 / 核心功能 / 里程碑</strong>
          </div>
        </div>
        <div class="flow-window">
          <div class="flow-node node-a">想法</div>
          <div class="flow-node node-b">PRD</div>
          <div class="flow-node node-c">流程图</div>
          <div class="flow-node node-d">任务</div>
          <svg viewBox="0 0 500 280" role="img" aria-label="流程图动画">
            <path class="flow-line line-one" d="M92 96 C170 64 210 74 250 116" />
            <path class="flow-line line-two" d="M276 142 C334 176 354 188 408 178" />
            <path class="flow-line line-three" d="M244 152 C208 198 178 222 124 222" />
          </svg>
        </div>
      </div>

      <div class="hero-copy">
        <h1>把一句话灵感，变成可执行的产品计划。</h1>
        <p>
          Think Land 面向个人创作者、独立开发者和小团队，把需求梳理、PRD 生成、流程图规划和任务拆解放进一个轻量工作台。
        </p>
        <div class="hero-actions">
          <RouterLink class="primary-btn" to="/login">开始使用</RouterLink>
          <a class="secondary-btn" href="#motion">查看生成动画</a>
        </div>
      </div>
    </section>

    <section id="product" class="intro-section">
      <div>
        <p class="eyebrow">网站介绍</p>
        <h2>它不是聊天框，而是帮你把想法往前推的产品搭子。</h2>
      </div>
      <div class="intro-grid">
        <article>
          <span>01</span>
          <h3>自然语言生成 PRD</h3>
          <p>输入你的产品想法，系统会整理目标用户、使用场景、功能范围和验收标准。</p>
        </article>
        <article>
          <span>02</span>
          <h3>自动生成业务流程</h3>
          <p>根据 PRD 识别关键节点，生成从用户触发到交付结果的流程图。</p>
        </article>
        <article>
          <span>03</span>
          <h3>沉淀个人项目空间</h3>
          <p>登录后可以保存创意、继续补充上下文，并把计划拆成可执行任务。</p>
        </article>
      </div>
    </section>

    <section id="motion" class="motion-section">
      <div class="motion-copy">
        <p class="eyebrow">动态演示</p>
        <h2>从一句话到 PRD，再到流程图。</h2>
        <p>首页演示的是未来核心体验：你描述目标，AI 先归纳需求，再将需求变成可以讨论和落地的流程结构。</p>
      </div>
      <div class="motion-board">
        <div class="prompt-stream">
          <span>用户输入</span>
          <strong>{{ activePrompt }}</strong>
        </div>
        <div class="generated-prd">
          <span>PRD 片段</span>
          <p v-for="item in prdItems" :key="item">{{ item }}</p>
        </div>
        <div class="mini-flow">
          <i>需求</i>
          <b></b>
          <i>页面</i>
          <b></b>
          <i>接口</i>
          <b></b>
          <i>交付</i>
        </div>
      </div>
    </section>

    <footer id="notice" class="site-footer">
      <div class="footer-brand">Think Land</div>
      <p>京ICP备20260519号-1 · AI 使用说明 · 信息收集说明 · 免责声明</p>
      <small>
        本网站提供的 AI 生成内容用于辅助创作和效率提升，可能存在不准确或不完整之处，请结合实际业务自行判断。我们仅收集完成注册、登录和产品体验所需的必要信息。
      </small>
    </footer>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const prompts = [
  '我想做一个能帮学生规划复习的 AI 工具',
  '帮我设计一个给自由职业者用的客户管理产品',
  '我要做一个能自动生成活动方案的小程序'
]

const index = ref(0)
let timer

const activePrompt = computed(() => prompts[index.value])
const prdItems = computed(() => [
  `目标用户：${index.value === 1 ? '自由职业者和小型工作室' : index.value === 2 ? '活动运营人员' : '学生和备考人群'}`,
  '核心价值：把零散想法整理成可执行计划',
  '关键路径：输入目标 -> 生成 PRD -> 生成流程图 -> 拆解任务'
])

onMounted(() => {
  timer = window.setInterval(() => {
    index.value = (index.value + 1) % prompts.length
  }, 3200)
})

onBeforeUnmount(() => {
  window.clearInterval(timer)
})
</script>
