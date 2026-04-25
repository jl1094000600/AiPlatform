<template>
  <div class="page-container benchmark-page">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">数据集测评</h2>
        <span class="total-count mono">Benchmark Platform</span>
      </div>
      <div class="header-actions">
        <el-button @click="showHistoryDialog = true" class="history-btn">
          <Clock /> 历史记录
        </el-button>
      </div>
    </div>

    <!-- Main Content - Tab Navigation -->
    <div class="benchmark-tabs glass-card">
      <div class="tab-nav">
        <button
          v-for="(tab, index) in tabs"
          :key="tab.id"
          class="tab-btn"
          :class="{ active: activeTab === index }"
          @click="activeTab = index"
        >
          <span class="tab-icon">{{ tab.icon }}</span>
          <span class="tab-name">{{ tab.name }}</span>
        </button>
      </div>

      <!-- Step 1: 数据集导入 -->
      <div v-show="activeTab === 0" class="tab-content">
        <DatasetImport @next="goToStep(1)" />
      </div>

      <!-- Step 2: 模拟数据生成 -->
      <div v-show="activeTab === 1" class="tab-content">
        <SimDataGenerator
          @back="goToStep(0)"
          @next="goToStep(2)"
        />
      </div>

      <!-- Step 3: Agent选择与执行 -->
      <div v-show="activeTab === 2" class="tab-content">
        <AgentSelector
          :dataset-id="datasetId"
          @back="goToStep(1)"
          @next="goToStep(3)"
        />
      </div>

      <!-- Step 4: 测评标准编辑 -->
      <div v-show="activeTab === 3" class="tab-content">
        <BenchmarkEditor
          @back="goToStep(2)"
          @next="goToStep(4)"
        />
      </div>

      <!-- Step 5: 结果可视化 -->
      <div v-show="activeTab === 4" class="tab-content">
        <ResultVisualization
          :benchmark-id="benchmarkId"
          @back="goToStep(3)"
        />
      </div>
    </div>

    <!-- History Dialog -->
    <el-dialog v-model="showHistoryDialog" title="测评历史记录" width="900px" class="history-dialog">
      <el-table :data="historyList" stripe v-loading="historyLoading">
        <el-table-column prop="id" label="ID" width="80" align="center">
          <template #default="{ row }">
            <span class="mono">#{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="benchmarkName" label="测评名称" min-width="160" />
        <el-table-column prop="datasetName" label="数据集" min-width="120" />
        <el-table-column prop="agentName" label="Agent" min-width="120" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <span class="status-badge" :class="getStatusClass(row.status)">
              {{ getStatusText(row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="总分" width="80" align="center">
          <template #default="{ row }">
            <span class="score-value mono">{{ row.score || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160">
          <template #default="{ row }">
            <span class="mono">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="viewHistoryResult(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Clock } from '@element-plus/icons-vue'
import api from '../api'
import DatasetImport from '../components/benchmark/DatasetImport.vue'
import SimDataGenerator from '../components/benchmark/SimDataGenerator.vue'
import AgentSelector from '../components/benchmark/AgentSelector.vue'
import BenchmarkEditor from '../components/benchmark/BenchmarkEditor.vue'
import ResultVisualization from '../components/benchmark/ResultVisualization.vue'

const activeTab = ref(0)
const datasetId = ref(null)
const benchmarkId = ref(null)

const tabs = [
  { id: 'import', name: '数据集导入', icon: '📁' },
  { id: 'generator', name: '模拟数据生成', icon: '⚙️' },
  { id: 'agent', name: 'Agent选择', icon: '🤖' },
  { id: 'standard', name: '测评标准', icon: '📐' },
  { id: 'result', name: '结果可视化', icon: '📊' }
]

const showHistoryDialog = ref(false)
const historyList = ref([])
const historyLoading = ref(false)

const goToStep = (step) => {
  activeTab.value = step
}

const loadHistory = async () => {
  historyLoading.value = true
  try {
    const res = await api.getBenchmarkHistory()
    if (res.data.code === 200) {
      historyList.value = res.data.data || []
    }
  } catch (e) {
    console.error('加载历史记录失败', e)
  } finally {
    historyLoading.value = false
  }
}

const viewHistoryResult = (row) => {
  benchmarkId.value = row.id
  showHistoryDialog.value = false
  activeTab.value = 4
}

const getStatusClass = (status) => {
  const map = { 'COMPLETED': 'success', 'RUNNING': 'running', 'FAILED': 'failed', 'PENDING': 'pending' }
  return map[status] || 'pending'
}

const getStatusText = (status) => {
  const map = { 'COMPLETED': '已完成', 'RUNNING': '进行中', 'FAILED': '失败', 'PENDING': '等待中' }
  return map[status] || status
}

const formatTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
}

onMounted(() => {
  loadHistory()
})
</script>

<style scoped>
.benchmark-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.benchmark-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.tab-nav {
  display: flex;
  gap: 4px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 500;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tab-btn:hover {
  background: rgba(0, 212, 255, 0.1);
  color: var(--text-secondary);
}

.tab-btn.active {
  background: linear-gradient(135deg, rgba(0, 212, 255, 0.2), rgba(155, 89, 255, 0.2));
  color: var(--neon-cyan);
  border: 1px solid rgba(0, 212, 255, 0.3);
}

.tab-icon {
  font-size: 16px;
}

.tab-name {
  white-space: nowrap;
}

.tab-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

.history-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  border-radius: 10px;
}

.history-btn:hover {
  border-color: var(--neon-cyan);
  color: var(--neon-cyan);
}

.status-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.status-badge.success {
  background: rgba(0, 255, 136, 0.15);
  color: var(--neon-green);
}

.status-badge.running {
  background: rgba(59, 130, 246, 0.15);
  color: #3b82f6;
}

.status-badge.failed {
  background: rgba(239, 68, 68, 0.15);
  color: var(--neon-pink);
}

.status-badge.pending {
  background: rgba(156, 163, 175, 0.15);
  color: #9ca3af;
}

.score-value {
  font-weight: 700;
  color: var(--neon-cyan);
}
</style>