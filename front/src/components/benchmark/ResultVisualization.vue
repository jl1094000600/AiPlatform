<template>
  <div class="result-visualization">
    <div class="section-header">
      <h3 class="section-title">测评结果</h3>
      <p class="section-desc">查看Agent在各个指标上的测评结果</p>
    </div>

    <!-- Summary Cards -->
    <div class="summary-cards">
      <div class="summary-card">
        <div class="card-icon">📊</div>
        <div class="card-info">
          <span class="card-label">总分</span>
          <span class="card-value mono">{{ summary.totalScore || '-' }}</span>
        </div>
      </div>
      <div class="summary-card">
        <div class="card-icon">✅</div>
        <div class="card-info">
          <span class="card-label">通过率</span>
          <span class="card-value mono">{{ summary.passRate || '-' }}%</span>
        </div>
      </div>
      <div class="summary-card">
        <div class="card-icon">⏱️</div>
        <div class="card-info">
          <span class="card-label">平均耗时</span>
          <span class="card-value mono">{{ summary.avgDuration || '-' }}ms</span>
        </div>
      </div>
      <div class="summary-card">
        <div class="card-icon">🎯</div>
        <div class="card-info">
          <span class="card-label">Agent数量</span>
          <span class="card-value mono">{{ summary.agentCount || 0 }}</span>
        </div>
      </div>
    </div>

    <!-- Charts Section -->
    <div class="charts-section">
      <!-- Bar Chart -->
      <div class="chart-card">
        <h4 class="chart-title">Agent评分对比</h4>
        <div ref="barChartRef" class="chart-container"></div>
      </div>

      <!-- Radar Chart -->
      <div class="chart-card">
        <h4 class="chart-title">能力雷达图</h4>
        <div ref="radarChartRef" class="chart-container"></div>
      </div>
    </div>

    <!-- Trend Chart -->
    <div class="chart-card full-width">
      <h4 class="chart-title">测评趋势</h4>
      <div ref="trendChartRef" class="chart-container"></div>
    </div>

    <!-- Detailed Table -->
    <div class="detail-section">
      <h4 class="sub-title">详细结果</h4>
      <el-table :data="resultDetails" stripe class="detail-table">
        <el-table-column prop="agentName" label="Agent" min-width="160">
          <template #default="{ row }">
            <div class="agent-cell">
              <span class="agent-name">{{ row.agentName }}</span>
              <span class="agent-code mono">#{{ row.agentId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          v-for="col in metricColumns"
          :key="col"
          :prop="col"
          :label="col"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <span class="metric-value mono" :class="getMetricClass(row[col])">
              {{ row[col] !== undefined ? row[col] : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="totalScore" label="总分" width="100" align="center">
          <template #default="{ row }">
            <span class="total-score mono">{{ row.totalScore || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="rank" label="排名" width="80" align="center">
          <template #default="{ row }">
            <span class="rank-badge" :class="getRankClass(row.rank)">
              #{{ row.rank }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Actions -->
    <div class="visualization-actions">
      <el-button @click="handleBack" class="back-btn">上一步</el-button>
      <el-button @click="handleExport" class="export-btn">
        <el-icon><Download /></el-icon> 导出报告
      </el-button>
      <el-button type="primary" @click="handleNewBenchmark" class="new-btn">
        新建测评
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import api from '../../api'

const props = defineProps({
  benchmarkId: [String, Number]
})

const emit = defineEmits(['back'])

const summary = ref({
  totalScore: 0,
  passRate: 0,
  avgDuration: 0,
  agentCount: 0
})

const resultDetails = ref([])
const metricColumns = ref(['准确性', '响应时间', '稳定性', '用户体验'])
const barChartRef = ref(null)
const radarChartRef = ref(null)
const trendChartRef = ref(null)

const loadResults = async () => {
  try {
    const res = await api.getBenchmarkResult(props.benchmarkId)
    if (res.data.code === 200) {
      const data = res.data.data

      summary.value = {
        totalScore: data.totalScore || 0,
        passRate: data.passRate || 0,
        avgDuration: data.avgDuration || 0,
        agentCount: data.agentCount || 0
      }

      resultDetails.value = data.details || []

      if (data.details && data.details.length > 0) {
        metricColumns.value = Object.keys(data.details[0]).filter(
          k => !['agentId', 'agentName', 'totalScore', 'rank'].includes(k)
        )
      }

      await nextTick()
      renderBarChart(data.agentScores || [])
      renderRadarChart(data.radarData || {})
      renderTrendChart(data.trendData || [])
    }
  } catch (e) {
    console.error('加载测评结果失败', e)
  }
}

const renderBarChart = (data) => {
  if (!barChartRef.value) return

  const chart = echarts.init(barChartRef.value)

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(10, 10, 15, 0.9)',
      borderColor: 'rgba(0, 212, 255, 0.3)',
      textStyle: { color: '#fff' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: data.map(d => d.agentName),
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.2)' } },
      axisLabel: { color: 'rgba(255,255,255,0.7)' }
    },
    yAxis: {
      type: 'value',
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.2)' } },
      axisLabel: { color: 'rgba(255,255,255,0.7)' },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } }
    },
    series: [
      {
        name: '评分',
        type: 'bar',
        data: data.map(d => d.score),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#00d4ff' },
            { offset: 1, color: '#9b59ff' }
          ]),
          borderRadius: [6, 6, 0, 0]
        },
        barWidth: '50%'
      }
    ]
  }

  chart.setOption(option)
}

const renderRadarChart = (data) => {
  if (!radarChartRef.value) return

  const chart = echarts.init(radarChartRef.value)

  const indicators = (data.indicators || []).map(ind => ({
    name: ind,
    max: 100
  }))

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      backgroundColor: 'rgba(10, 10, 15, 0.9)',
      borderColor: 'rgba(0, 212, 255, 0.3)',
      textStyle: { color: '#fff' }
    },
    radar: {
      indicator: indicators,
      shape: 'polygon',
      splitNumber: 5,
      axisName: {
        color: 'rgba(255,255,255,0.7)'
      },
      splitLine: {
        lineStyle: { color: 'rgba(0, 212, 255, 0.2)' }
      },
      splitArea: {
        areaStyle: { color: ['rgba(0, 212, 255, 0.05)', 'transparent'] }
      },
      axisLine: { lineStyle: { color: 'rgba(0, 212, 255, 0.2)' } }
    },
    series: [
      {
        type: 'radar',
        data: (data.values || []).map((v, i) => ({
          value: v,
          name: data.agents?.[i] || `Agent ${i + 1}`,
          areaStyle: {
            color: 'rgba(0, 212, 255, 0.15)'
          },
          lineStyle: {
            color: '#00d4ff',
            width: 2
          },
          itemStyle: {
            color: '#00d4ff'
          }
        }))
      }
    ]
  }

  chart.setOption(option)
}

const renderTrendChart = (data) => {
  if (!trendChartRef.value) return

  const chart = echarts.init(trendChartRef.value)

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(10, 10, 15, 0.9)',
      borderColor: 'rgba(0, 212, 255, 0.3)',
      textStyle: { color: '#fff' }
    },
    legend: {
      data: data.legend || ['分数'],
      textStyle: { color: 'rgba(255,255,255,0.7)' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: data.xAxis || [],
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.2)' } },
      axisLabel: { color: 'rgba(255,255,255,0.7)' }
    },
    yAxis: {
      type: 'value',
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.2)' } },
      axisLabel: { color: 'rgba(255,255,255,0.7)' },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } }
    },
    series: (data.series || [{ name: '分数', type: 'line', data: [], smooth: true }]).map(s => ({
      ...s,
      smooth: true,
      itemStyle: {
        color: s.color || '#00d4ff'
      },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(0, 212, 255, 0.3)' },
          { offset: 1, color: 'rgba(0, 212, 255, 0.05)' }
        ])
      }
    }))
  }

  chart.setOption(option)
}

const getMetricClass = (value) => {
  if (value === undefined || value === null) return ''
  if (value >= 90) return 'excellent'
  if (value >= 70) return 'good'
  if (value >= 60) return 'fair'
  return 'poor'
}

const getRankClass = (rank) => {
  if (rank === 1) return 'gold'
  if (rank === 2) return 'silver'
  if (rank === 3) return 'bronze'
  return ''
}

const handleBack = () => {
  emit('back')
}

const handleExport = async () => {
  try {
    const res = await api.exportBenchmarkReport(props.benchmarkId)
    if (res.data.code === 200) {
      ElMessage.success('报告已导出')
    }
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

const handleNewBenchmark = () => {
  emit('back')
}

onMounted(() => {
  loadResults()
})
</script>

<style scoped>
.result-visualization {
  max-width: 1100px;
}

.section-header {
  margin-bottom: 24px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.section-desc {
  font-size: 14px;
  color: var(--text-muted);
}

.sub-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.summary-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 32px;
}

.summary-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: var(--glass-bg);
  border: 1px solid var(--border-color);
  border-radius: 16px;
}

.card-icon {
  font-size: 32px;
}

.card-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-label {
  font-size: 12px;
  color: var(--text-muted);
}

.card-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--neon-cyan);
}

.charts-section {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.chart-card {
  padding: 20px;
  background: var(--glass-bg);
  border: 1px solid var(--border-color);
  border-radius: 16px;
}

.chart-card.full-width {
  grid-column: 1 / -1;
}

.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.chart-container {
  height: 280px;
}

.detail-section {
  margin-top: 24px;
}

.detail-table {
  border-radius: 12px;
}

.agent-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.agent-name {
  font-weight: 600;
  color: var(--text-primary);
}

.agent-code {
  font-size: 11px;
  color: var(--text-muted);
}

.metric-value {
  font-weight: 600;
}

.metric-value.excellent {
  color: var(--neon-green);
}

.metric-value.good {
  color: var(--neon-cyan);
}

.metric-value.fair {
  color: var(--neon-orange);
}

.metric-value.poor {
  color: var(--neon-pink);
}

.total-score {
  font-size: 16px;
  font-weight: 700;
  color: var(--neon-cyan);
}

.rank-badge {
  display: inline-block;
  width: 36px;
  height: 36px;
  line-height: 36px;
  text-align: center;
  border-radius: 50%;
  font-weight: 700;
  font-size: 14px;
}

.rank-badge.gold {
  background: linear-gradient(135deg, #ffd700, #ffaa00);
  color: #000;
}

.rank-badge.silver {
  background: linear-gradient(135deg, #c0c0c0, #a0a0a0);
  color: #000;
}

.rank-badge.bronze {
  background: linear-gradient(135deg, #cd7f32, #b06020);
  color: #fff;
}

.visualization-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--border-color);
}

.back-btn {
  padding: 12px 24px;
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  border-radius: 10px;
}

.export-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border: 1px solid var(--neon-cyan);
  background: transparent;
  color: var(--neon-cyan);
  border-radius: 10px;
}

.new-btn {
  padding: 12px 32px;
  background: linear-gradient(135deg, var(--neon-cyan), var(--neon-purple));
  border: none;
  color: #000;
  font-weight: 600;
  border-radius: 10px;
}
</style>