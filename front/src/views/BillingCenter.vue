<template>
  <div class="ops-page">
    <div class="ops-header">
      <div>
        <h2>成本与计费中心</h2>
        <span class="muted">按时间、Agent和业务线查看Token消耗与成本</span>
      </div>
      <div class="actions">
        <el-button @click="exportBill">导出账单</el-button>
        <el-button type="primary" @click="loadAll">查询</el-button>
      </div>
    </div>

    <div class="filter-row">
      <el-date-picker v-model="dateRange" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" />
      <el-input v-model="filters.agentId" placeholder="Agent ID" clearable />
      <el-input v-model="filters.bizModuleId" placeholder="业务线ID" clearable />
    </div>

    <div class="metric-grid">
      <div class="metric-card">
        <span>调用次数</span>
        <strong class="mono">{{ usage.totalCalls || 0 }}</strong>
      </div>
      <div class="metric-card">
        <span>Token消耗</span>
        <strong class="mono">{{ usage.totalTokens || 0 }}</strong>
      </div>
      <div class="metric-card">
        <span>估算成本</span>
        <strong class="mono">¥{{ usage.totalCost || 0 }}</strong>
      </div>
    </div>

    <div class="ops-grid two">
      <section class="ops-panel">
        <div class="panel-title">成本趋势</div>
        <div class="trend-list">
          <div v-for="point in trendRecords" :key="point.date" class="trend-row">
            <span class="mono">{{ point.date }}</span>
            <div class="trend-bar"><i :style="{ width: barWidth(point.cost) }"></i></div>
            <span class="mono">¥{{ point.cost }}</span>
            <span class="mono">{{ point.tokens }} Token</span>
          </div>
        </div>
      </section>

      <section class="ops-panel">
        <div class="panel-title">预算配置</div>
        <el-form :model="budgetForm" label-position="top">
          <el-form-item label="预算名称"><el-input v-model="budgetForm.budgetName" /></el-form-item>
          <el-form-item label="范围"><el-select v-model="budgetForm.scopeType"><el-option label="全局" value="GLOBAL" /><el-option label="Agent" value="AGENT" /></el-select></el-form-item>
          <el-form-item label="金额"><el-input-number v-model="budgetForm.amount" :min="0" /></el-form-item>
          <el-form-item label="告警阈值(%)"><el-input-number v-model="budgetForm.alertThreshold" :min="1" :max="100" /></el-form-item>
          <el-button type="primary" @click="saveBudget">保存预算</el-button>
        </el-form>
        <el-table :data="budgets" size="small" class="mini-table">
          <el-table-column prop="budgetName" label="名称" />
          <el-table-column prop="amount" label="金额" width="100" />
          <el-table-column prop="alertThreshold" label="阈值" width="90" />
        </el-table>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const dateRange = ref([])
const filters = reactive({ agentId: '', bizModuleId: '' })
const usage = ref({})
const trendRecords = ref([])
const budgets = ref([])
const budgetForm = reactive({ budgetName: '月度AI预算', scopeType: 'GLOBAL', amount: 1000, alertThreshold: 80, status: 1 })

const params = () => ({
  startDate: dateRange.value?.[0] ? formatDate(dateRange.value[0]) : undefined,
  endDate: dateRange.value?.[1] ? formatDate(dateRange.value[1]) : undefined,
  agentId: filters.agentId || undefined,
  bizModuleId: filters.bizModuleId || undefined
})

const formatDate = (value) => new Date(value).toISOString().slice(0, 10)
const barWidth = (value) => {
  const max = Math.max(...trendRecords.value.map(item => Number(item.cost) || 0), 1)
  return `${Math.max(4, (Number(value) / max) * 100)}%`
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
.muted { color: var(--text-muted); }
.filter-row { margin-bottom: 18px; }
.filter-row .el-input { width: 160px; }
.metric-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 18px; }
.metric-card, .ops-panel { background: var(--glass-bg); border: 1px solid var(--glass-border); border-radius: 8px; padding: 18px; }
.metric-card strong { display: block; font-size: 28px; margin-top: 8px; color: var(--accent-cyan); }
.ops-grid.two { display: grid; grid-template-columns: 1.5fr 1fr; gap: 18px; }
.panel-title { font-weight: 700; margin-bottom: 14px; }
.trend-list { display: flex; flex-direction: column; gap: 10px; max-height: 440px; overflow: auto; }
.trend-row { display: grid; grid-template-columns: 110px 1fr 90px 130px; gap: 12px; align-items: center; color: var(--text-secondary); }
.trend-bar { height: 10px; background: rgba(255,255,255,.08); border-radius: 5px; overflow: hidden; }
.trend-bar i { display: block; height: 100%; background: var(--neon-success); }
.mini-table { margin-top: 16px; }
</style>
