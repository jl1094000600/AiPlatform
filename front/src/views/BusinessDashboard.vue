<template>
  <div class="ops-page">
    <div class="ops-header">
      <div>
        <h2>首页概况</h2>
        <span class="muted">关键运行、成本和异常指标一屏查看</span>
      </div>
      <el-button type="primary" @click="loadAll">刷新</el-button>
    </div>

    <div class="metric-grid">
      <div class="metric-card" v-for="item in metrics" :key="item.label">
        <span class="metric-label">{{ item.label }}</span>
        <strong class="mono">{{ item.value }}</strong>
        <small>{{ item.hint }}</small>
      </div>
    </div>

    <div class="ops-grid two">
      <section class="ops-panel">
        <div class="panel-title">24小时趋势</div>
        <div class="trend-list">
          <div v-for="point in trendRecords" :key="point.time" class="trend-row">
            <span class="mono">{{ point.time }}</span>
            <div class="trend-bar">
              <i :style="{ width: barWidth(point.calls) }"></i>
            </div>
            <span class="mono">{{ point.calls }} 次</span>
            <span class="mono">{{ point.avgResponseTime }}ms</span>
            <span class="mono">¥{{ point.cost }}</span>
          </div>
        </div>
      </section>

      <section class="ops-panel">
        <div class="panel-title">异常标记</div>
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
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const summary = ref({})
const trendRecords = ref([])
const exceptions = ref([])

const metrics = computed(() => [
  { label: '今日调用量', value: summary.value.todayCalls ?? 0, hint: '业务请求总量' },
  { label: '在线Agent', value: summary.value.onlineAgents ?? 0, hint: '当前可调度节点' },
  { label: '平均响应', value: `${summary.value.avgResponseTime ?? 0}ms`, hint: '今日平均耗时' },
  { label: '今日成本', value: `¥${summary.value.todayCost ?? 0}`, hint: '按Token估算' },
  { label: '成功率', value: `${summary.value.successRate ?? 100}%`, hint: '成功调用占比' },
  { label: 'Token消耗', value: summary.value.totalTokens ?? 0, hint: '输入+输出Token' }
])

const barWidth = (value) => {
  const max = Math.max(...trendRecords.value.map(item => item.calls || 0), 1)
  return `${Math.max(4, (value / max) * 100)}%`
}

const loadAll = async () => {
  try {
    const [summaryRes, trendsRes, exceptionsRes] = await Promise.all([
      api.getDashboardSummary(),
      api.getDashboardTrends(),
      api.getDashboardExceptions()
    ])
    summary.value = summaryRes.data.data || {}
    trendRecords.value = trendsRes.data.data?.records || []
    exceptions.value = exceptionsRes.data.data || []
  } catch (error) {
    ElMessage.error('加载业务驾驶舱失败')
  }
}

onMounted(loadAll)
</script>

<style scoped>
.ops-page { padding: 24px; color: var(--text-primary); }
.ops-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.ops-header h2 { font-size: 26px; margin-bottom: 6px; }
.muted { color: var(--text-muted); }
.metric-grid { display: grid; grid-template-columns: repeat(6, minmax(140px, 1fr)); gap: 14px; margin-bottom: 18px; }
.metric-card, .ops-panel { background: var(--glass-bg); border: 1px solid var(--glass-border); border-radius: 8px; padding: 18px; }
.metric-card { display: flex; flex-direction: column; gap: 8px; min-height: 118px; }
.metric-card strong { font-size: 28px; color: var(--accent-cyan); }
.metric-label { color: var(--text-secondary); }
.metric-card small { color: var(--text-muted); }
.ops-grid.two { display: grid; grid-template-columns: 1.5fr 1fr; gap: 18px; }
.panel-title { font-size: 18px; font-weight: 700; margin-bottom: 16px; }
.trend-list { display: flex; flex-direction: column; gap: 9px; max-height: 520px; overflow: auto; }
.trend-row { display: grid; grid-template-columns: 60px 1fr 70px 80px 80px; align-items: center; gap: 12px; color: var(--text-secondary); }
.trend-bar { height: 10px; border-radius: 5px; background: #eef2f7; overflow: hidden; }
.trend-bar i { display: block; height: 100%; background: var(--neon-primary); }
.exception-list { display: flex; flex-direction: column; gap: 12px; }
.exception-item { display: flex; gap: 12px; align-items: flex-start; padding: 12px; border: 1px solid var(--border-color); border-radius: 8px; }
.exception-item p { color: var(--text-muted); margin-top: 4px; }
.quick-links { display: flex; gap: 10px; margin-top: 20px; }
.quick-links a { color: var(--accent-cyan); text-decoration: none; border: 1px solid var(--border-color); padding: 8px 10px; border-radius: 8px; }
@media (max-width: 1200px) {
  .metric-grid { grid-template-columns: repeat(3, 1fr); }
  .ops-grid.two { grid-template-columns: 1fr; }
}
</style>
