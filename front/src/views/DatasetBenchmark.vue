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
          @next="(data) => goToStep(2, data)"
        />
      </div>

      <!-- Step 3: Agent选择与执行 -->
      <div v-show="activeTab === 2" class="tab-content">
        <AgentSelector
          :dataset-id="datasetId"
          @back="goToStep(1)"
          @next="(data) => goToStep(3, data)"
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
      <div v-if="!historyError" class="history-content">
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
          <template #empty>
            <div class="empty-history">
              <div class="empty-icon">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
                  <line x1="16" y1="2" x2="16" y2="6"/>
                  <line x1="8" y1="2" x2="8" y2="6"/>
                  <line x1="3" y1="10" x2="21" y2="10"/>
                </svg>
              </div>
              <div class="empty-text">暂无测评记录</div>
              <div class="empty-hint">开始一个新的测评任务来查看结果</div>
            </div>
          </template>
        </el-table>
      </div>
      <div v-else class="history-error">
        <div class="error-icon">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10"/>
            <path d="M15 9l-6 6M9 9l6 6"/>
          </svg>
        </div>
        <div class="error-text">{{ historyError }}</div>
        <el-button type="primary" size="small" @click="loadHistory">重试</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
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
const historyError = ref(null)

const goToStep = (step, data) => {
  // Validate step access - can only go forward if previous steps are complete
  if (step > activeTab.value) {
    // Check required data for jumping ahead
    if (step >= 1 && !datasetId.value) {
      ElMessage.warning('请先完成数据集导入')
      return
    }
    if (step >= 3 && !benchmarkId.value) {
      ElMessage.warning('请先完成Agent选择和执行')
      return
    }
  }

  // Handle data from child components
  if (data) {
    if (data.datasetId) {
      datasetId.value = data.datasetId
    }
    if (data.benchmarkId) {
      benchmarkId.value = data.benchmarkId
    }
  }

  activeTab.value = step
}

const loadHistory = async () => {
  historyLoading.value = true
  historyError.value = null
  try {
    const res = await api.getBenchmarkHistory()
    if (res.data.code === 200) {
      historyList.value = res.data.data || []
    } else {
      historyError.value = res.data.message || '加载历史记录失败'
    }
  } catch (e) {
    console.error('加载历史记录失败', e)
    historyError.value = e.message || '网络错误，请检查连接'
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

.history-content {
  min-height: 200px;
}

.empty-history {
  padding: 40px 0;
  text-align: center;
}

.empty-history .empty-icon {
  color: var(--text-muted);
  margin-bottom: 16px;
}

.empty-history .empty-text {
  font-size: 16px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.empty-history .empty-hint {
  font-size: 14px;
  color: var(--text-muted);
}

.history-error {
  padding: 40px 0;
  text-align: center;
}

.history-error .error-icon {
  color: var(--neon-pink);
  margin-bottom: 16px;
}

.history-error .error-text {
  font-size: 14px;
  color: var(--text-muted);
  margin-bottom: 16px;
}
</style>