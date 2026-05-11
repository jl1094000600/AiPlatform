<template>
  <div class="ops-page">
    <div class="ops-header">
      <div>
        <h2>首页概况</h2>
        <span class="muted">关键运行、流水线、模型训练和异常指标一屏查看</span>
      </div>
      <el-button type="primary" @click="loadAll">刷新</el-button>
    </div>

    <div class="metric-grid">
      <div
        class="metric-card"
        v-for="(item, index) in metrics"
        :key="item.label"
        :class="`tone-${index % 6}`"
        :style="{ '--enter-delay': `${index * 45}ms` }"
      >
        <span class="metric-accent"></span>
        <span class="metric-label">{{ item.label }}</span>
        <strong class="mono metric-value">{{ item.value }}</strong>
        <small>{{ item.hint }}</small>
      </div>
    </div>

    <div class="ops-grid overview">
      <section class="ops-panel trend-panel">
        <div class="panel-title-row">
          <div>
            <div class="panel-title"><span class="live-dot"></span>24小时趋势</div>
            <span class="panel-subtitle">最近 {{ compactTrendRecords.length }} 个时间点</span>
          </div>
        </div>
        <el-empty v-if="compactTrendRecords.length === 0" description="暂无趋势数据" />
        <div v-else class="trend-list compact">
          <div v-for="point in compactTrendRecords" :key="point.time" class="trend-row">
            <span class="mono">{{ point.time }}</span>
            <div class="trend-bar">
              <i :style="{ width: barWidth(point.calls) }"></i>
            </div>
            <span class="mono">{{ point.calls }}次</span>
            <span class="mono">{{ point.avgResponseTime }}ms</span>
          </div>
        </div>
      </section>

      <section class="ops-panel">
        <div class="panel-title-row">
          <div>
            <div class="panel-title"><span class="live-dot pipeline"></span>流水线概况</div>
            <span class="panel-subtitle">自动化需求到代码生成</span>
          </div>
          <router-link class="panel-link" to="/automation">查看</router-link>
        </div>
        <div class="stat-grid">
          <div class="stat-item">
            <span>总流水线</span>
            <strong>{{ pipelineStats.total }}</strong>
          </div>
          <div class="stat-item">
            <span>运行中</span>
            <strong>{{ pipelineStats.running }}</strong>
          </div>
          <div class="stat-item">
            <span>待审核</span>
            <strong>{{ pipelineStats.waitingApprovals }}</strong>
          </div>
          <div class="stat-item">
            <span>阶段通过率</span>
            <strong>{{ pipelineStats.passRate }}%</strong>
          </div>
        </div>
        <div class="progress-strip">
          <span>阶段通过率</span>
          <div class="progress-track">
            <i :style="{ width: `${pipelineStats.passRate}%` }"></i>
          </div>
        </div>
        <div class="mini-list">
          <div v-for="item in latestPipelines" :key="item.id" class="mini-row">
            <div>
              <strong>{{ item.requirementTitle || item.projectName || '未命名流水线' }}</strong>
              <span>{{ item.currentStage || '未开始' }}</span>
            </div>
            <el-tag size="small" :type="pipelineStatusType(item.status)">
              {{ pipelineStatusLabel(item.status) }}
            </el-tag>
          </div>
          <el-empty v-if="latestPipelines.length === 0" description="暂无流水线" />
        </div>
      </section>

      <section class="ops-panel">
        <div class="panel-title-row">
          <div>
            <div class="panel-title"><span class="live-dot training"></span>模型训练概况</div>
            <span class="panel-subtitle">训练任务状态与最近记录</span>
          </div>
          <router-link class="panel-link" to="/model-training">查看</router-link>
        </div>
        <div class="stat-grid">
          <div class="stat-item">
            <span>训练任务</span>
            <strong>{{ trainingStats.total }}</strong>
          </div>
          <div class="stat-item">
            <span>运行中</span>
            <strong>{{ trainingStats.running }}</strong>
          </div>
          <div class="stat-item">
            <span>已完成</span>
            <strong>{{ trainingStats.completed }}</strong>
          </div>
          <div class="stat-item">
            <span>失败</span>
            <strong>{{ trainingStats.failed }}</strong>
          </div>
        </div>
        <div class="progress-strip training-progress">
          <span>训练完成率</span>
          <div class="progress-track">
            <i :style="{ width: `${trainingCompletionRate}%` }"></i>
          </div>
        </div>
        <div class="mini-list">
          <div v-for="item in recentTrainingJobs" :key="item.id" class="mini-row">
            <div>
              <strong>{{ item.id }}</strong>
              <span>{{ trainingJobHint(item) }}</span>
            </div>
            <el-tag size="small" :type="trainingStatusType(item.status)">
              {{ trainingStatusLabel(item.status) }}
            </el-tag>
          </div>
          <el-empty v-if="recentTrainingJobs.length === 0" description="暂无训练任务" />
        </div>
      </section>
    </div>

    <section class="ops-panel exception-panel">
      <div class="panel-title-row">
        <div>
          <div class="panel-title">异常标记</div>
          <span class="panel-subtitle">按阈值聚合的关键风险</span>
        </div>
      </div>
      <el-empty v-if="exceptions.length === 0" description="暂无异常" />
      <div v-else class="exception-list">
        <div v-for="item in exceptions" :key="item.metric" class="exception-item">
          <el-tag :type="item.level === 'P0' ? 'danger' : 'warning'">{{ item.level }}</el-tag>
          <div>
            <strong>{{ item.title }}</strong>
            <p>当前 {{ item.value }}，阈值 {{ item.threshold }}</p>
          </div>
        </div>
      </div>
      <div class="quick-links">
        <router-link to="/agents">Agent详情</router-link>
        <router-link to="/monitor">接口监控</router-link>
        <router-link to="/alerts">告警中心</router-link>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const summary = ref({})
const trendRecords = ref([])
const exceptions = ref([])
const automationSummary = ref({})
const automationPipelines = ref([])
const trainingJobs = ref([])

const metrics = computed(() => [
  { label: '今日调用量', value: summary.value.todayCalls ?? 0, hint: '业务请求总量' },
  { label: '在线Agent', value: summary.value.onlineAgents ?? 0, hint: '当前可调度节点' },
  { label: '平均响应', value: `${summary.value.avgResponseTime ?? 0}ms`, hint: '今日平均耗时' },
  { label: '今日成本', value: `¥${summary.value.todayCost ?? 0}`, hint: '按token估算' },
  { label: '成功率', value: `${summary.value.successRate ?? 100}%`, hint: '成功调用占比' },
  { label: 'Token消耗', value: summary.value.totalTokens ?? 0, hint: '输入+输出Token' }
])

const compactTrendRecords = computed(() => trendRecords.value.slice(-8))

const latestPipelines = computed(() => automationPipelines.value.slice(0, 4))

const recentTrainingJobs = computed(() => trainingJobs.value.slice(0, 4))

const pipelineStats = computed(() => {
  const total = automationSummary.value.totalPipelines ?? automationPipelines.value.length
  const running = automationSummary.value.runningPipelines ?? automationPipelines.value.filter(item => isPipelineRunning(item.status)).length
  const waitingApprovals = automationSummary.value.waitingApprovals ?? automationPipelines.value.filter(item => item.approvalRequired === 1).length
  const passRate = automationSummary.value.stagePassRate ?? calcPipelinePassRate()
  return {
    total,
    running,
    waitingApprovals,
    passRate: Number(passRate || 0).toFixed(1)
  }
})

const trainingStats = computed(() => {
  const jobs = trainingJobs.value
  return {
    total: jobs.length,
    running: jobs.filter(item => ['PENDING', 'QUEUED', 'RUNNING', 'TRAINING'].includes((item.status || '').toUpperCase())).length,
    completed: jobs.filter(item => ['SUCCESS', 'COMPLETED', 'FINISHED'].includes((item.status || '').toUpperCase())).length,
    failed: jobs.filter(item => ['FAILED', 'ERROR'].includes((item.status || '').toUpperCase())).length
  }
})

const trainingCompletionRate = computed(() => {
  if (!trainingStats.value.total) return 0
  return Number((trainingStats.value.completed / trainingStats.value.total) * 100).toFixed(1)
})

const barWidth = (value) => {
  const max = Math.max(...trendRecords.value.map(item => item.calls || 0), 1)
  return `${Math.max(4, (value / max) * 100)}%`
}

const calcPipelinePassRate = () => {
  const totals = automationPipelines.value.reduce((acc, item) => {
    acc.passed += item.passedStages || 0
    acc.total += item.totalStages || 0
    return acc
  }, { passed: 0, total: 0 })
  return totals.total ? (totals.passed / totals.total) * 100 : 0
}

const isPipelineRunning = (status) => ['RUNNING', 'IN_PROGRESS', 'PROCESSING'].includes((status || '').toUpperCase())

const pipelineStatusLabel = (status) => {
  const labels = {
    DRAFT: '草稿',
    RUNNING: '运行中',
    IN_PROGRESS: '运行中',
    APPROVAL: '待审核',
    WAITING_APPROVAL: '待审核',
    SUCCESS: '已完成',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return labels[(status || '').toUpperCase()] || status || '未知'
}

const pipelineStatusType = (status) => {
  const normalized = (status || '').toUpperCase()
  if (['SUCCESS', 'COMPLETED'].includes(normalized)) return 'success'
  if (['FAILED', 'ERROR'].includes(normalized)) return 'danger'
  if (['APPROVAL', 'WAITING_APPROVAL'].includes(normalized)) return 'warning'
  if (['RUNNING', 'IN_PROGRESS', 'PROCESSING'].includes(normalized)) return 'primary'
  return 'info'
}

const trainingStatusLabel = (status) => {
  const labels = {
    PENDING: '待运行',
    QUEUED: '排队中',
    RUNNING: '运行中',
    TRAINING: '训练中',
    SUCCESS: '已完成',
    COMPLETED: '已完成',
    FINISHED: '已完成',
    FAILED: '失败',
    ERROR: '失败'
  }
  return labels[(status || '').toUpperCase()] || status || '未知'
}

const trainingStatusType = (status) => {
  const normalized = (status || '').toUpperCase()
  if (['SUCCESS', 'COMPLETED', 'FINISHED'].includes(normalized)) return 'success'
  if (['FAILED', 'ERROR'].includes(normalized)) return 'danger'
  if (['PENDING', 'QUEUED'].includes(normalized)) return 'warning'
  if (['RUNNING', 'TRAINING'].includes(normalized)) return 'primary'
  return 'info'
}

const trainingJobHint = (job) => {
  if (job.errorMessage) return job.errorMessage
  if (job.outputDir) return job.outputDir
  if (job.modelPath) return job.modelPath
  return job.createTime || '未记录路径'
}

const normalizePageRecords = (payload) => {
  if (Array.isArray(payload)) return payload
  return payload?.records || []
}

const loadAll = async () => {
  try {
    const [
      summaryRes,
      trendsRes,
      exceptionsRes,
      automationSummaryRes,
      automationPipelinesRes,
      trainingJobsRes
    ] = await Promise.all([
      api.getDashboardSummary(),
      api.getDashboardTrends(),
      api.getDashboardExceptions(),
      api.getAutomationSummary(),
      api.getAutomationPipelines({ pageNum: 1, pageSize: 5 }),
      api.getModelTrainingJobs()
    ])
    summary.value = summaryRes.data.data || {}
    trendRecords.value = trendsRes.data.data?.records || []
    exceptions.value = exceptionsRes.data.data || []
    automationSummary.value = automationSummaryRes.data.data || {}
    automationPipelines.value = normalizePageRecords(automationPipelinesRes.data.data)
    trainingJobs.value = normalizePageRecords(trainingJobsRes.data.data)
  } catch (error) {
    ElMessage.error('加载首页概况失败')
  }
}

onMounted(loadAll)
</script>

<style scoped>
.ops-page { padding: 24px; color: var(--text-primary); }
.ops-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; gap: 16px; }
.ops-header h2 { font-family: var(--font-display); font-size: 28px; line-height: 1.15; margin-bottom: 6px; font-weight: 800; letter-spacing: 0; }
.muted { color: var(--text-muted); }
.metric-grid { display: grid; grid-template-columns: repeat(6, minmax(140px, 1fr)); gap: 14px; margin-bottom: 18px; }
.metric-card, .ops-panel { background: var(--glass-bg); border: 1px solid var(--glass-border); border-radius: 8px; padding: 18px; box-shadow: 0 10px 28px rgba(15, 23, 42, .05); transition: transform .2s ease, border-color .2s ease, box-shadow .2s ease; }
.metric-card { position: relative; display: flex; flex-direction: column; gap: 8px; min-height: 118px; overflow: hidden; animation: panelIn .36s ease both; animation-delay: var(--enter-delay); }
.metric-card::after { content: ''; position: absolute; inset: 0; background: linear-gradient(115deg, transparent 0%, transparent 42%, rgba(37, 99, 235, .08) 50%, transparent 58%, transparent 100%); transform: translateX(-120%); transition: transform .5s ease; pointer-events: none; }
.metric-card:hover, .ops-panel:hover { transform: translateY(-2px); border-color: rgba(37, 99, 235, .3); box-shadow: 0 16px 34px rgba(15, 23, 42, .08); }
.metric-card:hover::after { transform: translateX(120%); }
.metric-accent { width: 34px; height: 3px; border-radius: 999px; background: var(--accent-cyan); margin-bottom: 2px; }
.metric-card.tone-1 .metric-accent { background: var(--accent-green); }
.metric-card.tone-2 .metric-accent { background: var(--accent-orange); }
.metric-card.tone-3 .metric-accent { background: var(--accent-magenta); }
.metric-card.tone-4 .metric-accent { background: var(--accent-purple); }
.metric-card.tone-5 .metric-accent { background: var(--accent-red); }
.metric-value { font-size: 30px; color: var(--accent-cyan); font-weight: 800; line-height: 1.1; font-variant-numeric: tabular-nums; }
.metric-label { color: var(--text-secondary); font-weight: 650; }
.metric-card small { color: var(--text-muted); }
.ops-grid.overview { display: grid; grid-template-columns: minmax(320px, .9fr) repeat(2, minmax(280px, 1fr)); gap: 18px; margin-bottom: 18px; align-items: stretch; }
.panel-title-row { display: flex; justify-content: space-between; align-items: flex-start; gap: 14px; margin-bottom: 14px; }
.panel-title { display: inline-flex; align-items: center; gap: 8px; font-size: 18px; font-weight: 800; line-height: 1.3; letter-spacing: 0; }
.panel-subtitle { display: block; color: var(--text-muted); margin-top: 4px; font-size: 13px; }
.panel-link { color: var(--accent-cyan); text-decoration: none; border: 1px solid var(--border-color); padding: 7px 10px; border-radius: 8px; line-height: 1; white-space: nowrap; transition: background .2s ease, border-color .2s ease, transform .2s ease; }
.panel-link:hover { background: rgba(37, 99, 235, .08); border-color: rgba(37, 99, 235, .35); transform: translateY(-1px); }
.live-dot { width: 9px; height: 9px; border-radius: 999px; color: var(--accent-cyan); background: currentColor; box-shadow: 0 0 0 0 rgba(37, 99, 235, .32); animation: statusPulse 1.8s ease-out infinite; }
.live-dot.pipeline { color: var(--accent-green); }
.live-dot.training { color: var(--accent-orange); }
.trend-list { display: flex; flex-direction: column; gap: 8px; overflow: auto; }
.trend-list.compact { max-height: 250px; }
.trend-row { display: grid; grid-template-columns: 54px minmax(80px, 1fr) 54px 64px; align-items: center; gap: 10px; color: var(--text-secondary); font-size: 13px; }
.trend-bar { height: 9px; border-radius: 5px; background: #eef2f7; overflow: hidden; }
.trend-bar i { display: block; height: 100%; background: linear-gradient(90deg, var(--neon-primary), var(--accent-green)); border-radius: inherit; animation: barGlow 2.4s ease-in-out infinite; }
.stat-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-bottom: 14px; }
.stat-item { border: 1px solid var(--border-color); border-radius: 8px; padding: 12px; min-height: 72px; display: flex; flex-direction: column; justify-content: space-between; background: linear-gradient(180deg, rgba(248, 250, 252, .84), rgba(255, 255, 255, .95)); transition: border-color .2s ease, transform .2s ease; }
.stat-item:hover { border-color: rgba(37, 99, 235, .28); transform: translateY(-1px); }
.stat-item span { color: var(--text-muted); font-size: 13px; font-weight: 600; }
.stat-item strong { color: var(--accent-cyan); font-size: 25px; line-height: 1.2; font-weight: 800; font-variant-numeric: tabular-nums; }
.progress-strip { display: grid; grid-template-columns: 76px 1fr; align-items: center; gap: 10px; margin: -2px 0 14px; color: var(--text-muted); font-size: 12px; font-weight: 650; }
.progress-track { height: 8px; border-radius: 999px; background: #e5e7eb; overflow: hidden; }
.progress-track i { display: block; height: 100%; max-width: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--accent-cyan), var(--accent-green)); transition: width .45s ease; }
.training-progress .progress-track i { background: linear-gradient(90deg, var(--accent-orange), var(--accent-green)); }
.mini-list { display: flex; flex-direction: column; gap: 9px; min-height: 132px; }
.mini-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 10px 0; border-top: 1px solid var(--border-color); transition: padding-left .2s ease, border-color .2s ease; }
.mini-row:hover { padding-left: 6px; border-color: rgba(37, 99, 235, .28); }
.mini-row > div { min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.mini-row strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 220px; }
.mini-row span { color: var(--text-muted); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 220px; }
.exception-panel { margin-top: 0; }
.exception-list { display: grid; grid-template-columns: repeat(2, minmax(260px, 1fr)); gap: 12px; }
.exception-item { display: flex; gap: 12px; align-items: flex-start; padding: 12px; border: 1px solid var(--border-color); border-radius: 8px; transition: border-color .2s ease, background .2s ease; }
.exception-item:hover { border-color: rgba(220, 38, 38, .22); background: rgba(254, 242, 242, .45); }
.exception-item p { color: var(--text-muted); margin-top: 4px; }
.quick-links { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 20px; }
.quick-links a { color: var(--accent-cyan); text-decoration: none; border: 1px solid var(--border-color); padding: 8px 10px; border-radius: 8px; transition: background .2s ease, transform .2s ease; }
.quick-links a:hover { background: rgba(37, 99, 235, .08); transform: translateY(-1px); }
@keyframes panelIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes statusPulse {
  0% { box-shadow: 0 0 0 0 currentColor; opacity: .9; }
  70% { box-shadow: 0 0 0 7px transparent; opacity: 1; }
  100% { box-shadow: 0 0 0 0 transparent; opacity: .9; }
}
@keyframes barGlow {
  0%, 100% { filter: saturate(1); }
  50% { filter: saturate(1.35) brightness(1.06); }
}
@media (max-width: 1400px) {
  .metric-grid { grid-template-columns: repeat(3, 1fr); }
  .ops-grid.overview { grid-template-columns: repeat(2, minmax(280px, 1fr)); }
  .trend-panel { grid-column: 1 / -1; }
}
@media (max-width: 900px) {
  .ops-page { padding: 16px; }
  .ops-header { align-items: flex-start; flex-direction: column; }
  .metric-grid, .ops-grid.overview, .exception-list { grid-template-columns: 1fr; }
  .trend-panel { grid-column: auto; }
  .trend-row { grid-template-columns: 48px minmax(70px, 1fr) 50px 58px; gap: 8px; }
}
</style>
