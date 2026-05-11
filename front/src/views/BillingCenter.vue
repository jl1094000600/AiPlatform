<template>
  <div class="ops-page">
    <div class="ops-header">
      <div>
        <h2>成本与计费中心</h2>
        <span class="muted">按时间、用户、Agent、业务线和平台内部流水线查看 Token 消耗与成本</span>
      </div>
      <div class="actions">
        <el-button @click="exportBill">导出账单</el-button>
        <el-button type="primary" @click="loadAll">查询</el-button>
      </div>
    </div>

    <div class="filter-row">
      <el-date-picker v-model="dateRange" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" />
      <el-input v-model="filters.userId" placeholder="用户 ID" clearable />
      <el-input v-model="filters.username" placeholder="用户名" clearable />
      <el-select v-model="filters.source" placeholder="来源" clearable>
        <el-option label="全部来源" value="" />
        <el-option label="Agent 调用" value="AGENT" />
        <el-option label="平台流水线" value="PIPELINE" />
      </el-select>
      <el-input v-model="filters.agentId" placeholder="Agent ID" clearable />
      <el-input v-model="filters.bizModuleId" placeholder="业务线 ID" clearable />
    </div>

    <div class="metric-grid">
      <div class="metric-card">
        <span>调用次数</span>
        <strong class="mono">{{ usage.totalCalls || 0 }}</strong>
        <small>Agent 调用 + 流水线生成任务</small>
      </div>
      <div class="metric-card">
        <span>Token 消耗</span>
        <strong class="mono">{{ usage.totalTokens || 0 }}</strong>
        <small>合并所有计费来源</small>
      </div>
      <div class="metric-card">
        <span>估算成本</span>
        <strong class="mono">¥{{ usage.totalCost || 0 }}</strong>
        <small>按当前平台单价估算</small>
      </div>
      <div class="metric-card">
        <span>流水线 Token</span>
        <strong class="mono">{{ usage.pipelineTokens || 0 }}</strong>
        <small>PRD / 代码生成消耗</small>
      </div>
      <div class="metric-card">
        <span>计费用户</span>
        <strong class="mono">{{ userSummaries.length }}</strong>
        <small>当前筛选范围内</small>
      </div>
    </div>

    <div class="ops-grid two">
      <section class="ops-panel">
        <div class="panel-title-row">
          <div>
            <div class="panel-title">成本趋势</div>
            <span class="panel-subtitle">绿色为 Agent 调用，蓝色为平台流水线</span>
          </div>
        </div>
        <div class="trend-list">
          <div v-for="point in trendRecords" :key="point.date" class="trend-row">
            <span class="mono">{{ point.date }}</span>
            <div class="stack-bar">
              <i class="agent" :style="{ width: stackedWidth(point.agentTokens, point.tokens) }"></i>
              <i class="pipeline" :style="{ width: stackedWidth(point.pipelineTokens, point.tokens) }"></i>
            </div>
            <span class="mono">¥{{ point.cost }}</span>
            <span class="mono">{{ point.tokens }} Token</span>
          </div>
        </div>
      </section>

      <section class="ops-panel">
        <div class="panel-title-row">
          <div>
            <div class="panel-title">来源拆分</div>
            <span class="panel-subtitle">平台内部流水线消耗已计入总成本</span>
          </div>
        </div>
        <div class="source-grid">
          <div>
            <span>Agent 调用</span>
            <strong class="mono">{{ usage.agentTokens || 0 }}</strong>
            <small>¥{{ usage.agentCost || 0 }}</small>
          </div>
          <div>
            <span>平台流水线</span>
            <strong class="mono">{{ usage.pipelineTokens || 0 }}</strong>
            <small>¥{{ usage.pipelineCost || 0 }}</small>
          </div>
        </div>

        <div class="panel-title compact">用户用量</div>
        <el-table :data="userSummaries" size="small" class="mini-table" max-height="260">
          <el-table-column label="用户" min-width="130">
            <template #default="{ row }">
              <div class="user-cell">
                <strong>{{ row.username || '未归属用户' }}</strong>
                <span v-if="row.userId">#{{ row.userId }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="calls" label="次数" width="72" />
          <el-table-column prop="tokens" label="Token" width="110" />
          <el-table-column prop="pipelineTokens" label="流水线" width="110" />
          <el-table-column label="成本" width="95">
            <template #default="{ row }">¥{{ row.cost }}</template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <section class="ops-panel budget-panel">
      <div class="panel-title">预算配置</div>
      <el-form :model="budgetForm" label-position="top" class="budget-form">
        <el-form-item label="预算名称"><el-input v-model="budgetForm.budgetName" /></el-form-item>
        <el-form-item label="范围">
          <el-select v-model="budgetForm.scopeType">
            <el-option label="全局" value="GLOBAL" />
            <el-option label="Agent" value="AGENT" />
            <el-option label="用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额"><el-input-number v-model="budgetForm.amount" :min="0" /></el-form-item>
        <el-form-item label="告警阈值(%)"><el-input-number v-model="budgetForm.alertThreshold" :min="1" :max="100" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveBudget">保存预算</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="budgets" size="small" class="mini-table">
        <el-table-column prop="budgetName" label="名称" />
        <el-table-column prop="scopeType" label="范围" width="110" />
        <el-table-column prop="amount" label="金额" width="100" />
        <el-table-column prop="alertThreshold" label="阈值" width="90" />
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const dateRange = ref([])
const filters = reactive({ userId: '', username: '', source: '', agentId: '', bizModuleId: '' })
const usage = ref({})
const trendRecords = ref([])
const budgets = ref([])
const budgetForm = reactive({ budgetName: '月度AI预算', scopeType: 'GLOBAL', amount: 1000, alertThreshold: 80, status: 1 })

const userSummaries = computed(() => usage.value.userSummaries || [])

const params = () => ({
  startDate: dateRange.value?.[0] ? formatDate(dateRange.value[0]) : undefined,
  endDate: dateRange.value?.[1] ? formatDate(dateRange.value[1]) : undefined,
  userId: filters.userId || undefined,
  username: filters.username || undefined,
  source: filters.source || undefined,
  agentId: filters.agentId || undefined,
  bizModuleId: filters.bizModuleId || undefined
})

const formatDate = (value) => new Date(value).toISOString().slice(0, 10)

const stackedWidth = (value, total) => {
  const current = Number(value) || 0
  const base = Math.max(Number(total) || 0, 1)
  return `${Math.max(current ? 4 : 0, (current / base) * 100)}%`
}

const loadAll = async () => {
  try {
    const [usageRes, trendsRes, budgetsRes] = await Promise.all([
      api.getBillingUsage(params()),
      api.getBillingCostTrends(params()),
      api.getBillingBudgets({ pageNum: 1, pageSize: 10 })
    ])
    usage.value = usageRes.data.data || {}
    trendRecords.value = trendsRes.data.data?.records || []
    budgets.value = budgetsRes.data.data?.records || []
  } catch {
    ElMessage.error('加载计费数据失败')
  }
}

const saveBudget = async () => {
  await api.createBillingBudget(budgetForm)
  ElMessage.success('预算已保存')
  loadAll()
}

const exportBill = async () => {
  const res = await api.exportBilling(params())
  const url = URL.createObjectURL(new Blob([res.data]))
  const link = document.createElement('a')
  link.href = url
  link.download = 'billing.csv'
  link.click()
  URL.revokeObjectURL(url)
}

onMounted(loadAll)
</script>

<style scoped>
.ops-page { padding: 24px; color: var(--text-primary); }
.ops-header, .actions, .filter-row { display: flex; align-items: center; gap: 12px; }
.ops-header { justify-content: space-between; margin-bottom: 18px; }
.ops-header h2 { font-size: 26px; margin-bottom: 6px; }
.muted, .panel-subtitle { color: var(--text-muted); }
.filter-row { margin-bottom: 18px; flex-wrap: wrap; }
.filter-row .el-input { width: 150px; }
.filter-row .el-select { width: 150px; }
.metric-grid { display: grid; grid-template-columns: repeat(5, minmax(150px, 1fr)); gap: 14px; margin-bottom: 18px; }
.metric-card, .ops-panel { background: var(--glass-bg); border: 1px solid var(--glass-border); border-radius: 8px; padding: 18px; }
.metric-card strong { display: block; font-size: 28px; margin-top: 8px; color: var(--accent-cyan); }
.metric-card small { display: block; color: var(--text-muted); margin-top: 6px; }
.ops-grid.two { display: grid; grid-template-columns: 1.35fr 1fr; gap: 18px; margin-bottom: 18px; }
.panel-title-row { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.panel-title { font-weight: 700; margin-bottom: 14px; }
.panel-title.compact { margin-top: 18px; }
.trend-list { display: flex; flex-direction: column; gap: 10px; max-height: 440px; overflow: auto; }
.trend-row { display: grid; grid-template-columns: 110px 1fr 90px 130px; gap: 12px; align-items: center; color: var(--text-secondary); }
.stack-bar { display: flex; height: 10px; background: #eef2f7; border-radius: 5px; overflow: hidden; }
.stack-bar i { display: block; height: 100%; min-width: 0; }
.stack-bar .agent { background: var(--accent-green); }
.stack-bar .pipeline { background: var(--accent-cyan); }
.source-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.source-grid > div { border: 1px solid var(--border-color); border-radius: 8px; padding: 14px; }
.source-grid span, .source-grid small { color: var(--text-muted); }
.source-grid strong { display: block; color: var(--accent-cyan); font-size: 24px; margin: 8px 0 4px; }
.user-cell { display: flex; flex-direction: column; gap: 3px; }
.user-cell span { color: var(--text-muted); font-size: 12px; }
.budget-panel { margin-bottom: 18px; }
.budget-form { display: grid; grid-template-columns: 1.4fr 1fr 1fr 1fr auto; gap: 12px; align-items: end; }
.budget-form :deep(.el-form-item) { margin-bottom: 0; }
.mini-table { margin-top: 16px; }
@media (max-width: 1280px) {
  .metric-grid { grid-template-columns: repeat(3, 1fr); }
  .ops-grid.two, .budget-form { grid-template-columns: 1fr; }
}
@media (max-width: 760px) {
  .ops-page { padding: 16px; }
  .ops-header { flex-direction: column; align-items: flex-start; }
  .metric-grid { grid-template-columns: 1fr; }
  .trend-row { grid-template-columns: 96px 1fr; }
  .trend-row span:nth-child(3), .trend-row span:nth-child(4) { justify-self: start; }
}
</style>
