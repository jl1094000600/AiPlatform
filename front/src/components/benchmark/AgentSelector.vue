<template>
  <div class="agent-selector">
    <div class="section-header">
      <h3 class="section-title">Agent 选择与测评执行</h3>
      <p class="section-desc">选择要测评的Agent，配置执行参数后开始测评</p>
    </div>

    <!-- Agent Selection -->
    <div class="agent-section">
      <h4 class="sub-title">选择 Agent</h4>
      <el-table
        :data="agents"
        stripe
        v-loading="loading"
        @selection-change="handleSelectionChange"
        class="agent-table"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="80" align="center">
          <template #default="{ row }">
            <span class="mono">#{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="agentName" label="名称" min-width="160">
          <template #default="{ row }">
            <div class="agent-cell">
              <span class="agent-name">{{ row.agentName }}</span>
              <span class="agent-code mono">{{ row.agentCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            <span class="category-tag">{{ row.category || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <span class="status-badge" :class="getStatusClass(row.status)">
              <span class="status-dot"></span>
              {{ getStatusText(row.status) }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Execution Config -->
    <div v-if="selectedAgents.length > 0" class="config-section">
      <h4 class="sub-title">执行配置</h4>

      <div class="config-grid">
        <div class="config-item">
          <span class="config-label">采样数量</span>
          <el-input-number v-model="config.sampleCount" :min="1" :max="1000" />
          <span class="config-hint">每个Agent测试的数据条数</span>
        </div>

        <div class="config-item">
          <span class="config-label">超时时间</span>
          <el-input-number v-model="config.timeout" :min="5000" :max="300000" :step="5000" />
          <span class="config-hint">单次调用超时（毫秒）</span>
        </div>

        <div class="config-item">
          <span class="config-label">重试次数</span>
          <el-input-number v-model="config.retryCount" :min="0" :max="5" />
          <span class="config-hint">失败后的重试次数</span>
        </div>

        <div class="config-item">
          <span class="config-label">并发数</span>
          <el-input-number v-model="config.concurrency" :min="1" :max="20" />
          <span class="config-hint">同时执行的并发数</span>
        </div>
      </div>
    </div>

    <!-- Progress -->
    <div v-if="isRunning" class="progress-section">
      <h4 class="sub-title">执行进度</h4>
      <div class="progress-info">
        <div class="progress-stats">
          <div class="stat-item">
            <span class="stat-label">已完成</span>
            <span class="stat-value mono">{{ progress.completed }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">进行中</span>
            <span class="stat-value mono">{{ progress.running }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">失败</span>
            <span class="stat-value mono error">{{ progress.failed }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">总计</span>
            <span class="stat-value mono">{{ progress.total }}</span>
          </div>
        </div>
        <el-progress
          :percentage="progress.percentage"
          :stroke-width="8"
          :color="progressColor"
        />
        <div class="progress-detail">
          <span>正在测试: {{ currentAgent }}</span>
          <span class="mono">耗时: {{ progress.elapsed }}s</span>
        </div>
      </div>
    </div>

    <!-- Actions -->
    <div class="selector-actions">
      <el-button @click="handleBack" class="back-btn">上一步</el-button>
      <el-button
        type="primary"
        :disabled="selectedAgents.length === 0"
        :loading="isRunning"
        @click="handleStart"
        class="start-btn"
      >
        {{ isRunning ? '测评中...' : '开始测评' }}
      </el-button>
      <el-button
        type="primary"
        :disabled="!canProceed"
        @click="handleProceed"
        class="proceed-btn"
      >
        下一步
        <el-icon><ArrowRight /></el-icon>
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import api from '../../api'

const props = defineProps({
  datasetId: [String, Number]
})

const emit = defineEmits(['back', 'next'])

const agents = ref([])
const loading = ref(false)
const selectedAgents = ref([])
const isRunning = ref(false)
const benchmarkId = ref(null)

const config = ref({
  sampleCount: 10,
  timeout: 30000,
  retryCount: 2,
  concurrency: 5
})

const progress = ref({
  completed: 0,
  running: 0,
  failed: 0,
  total: 0,
  percentage: 0,
  elapsed: 0
})

const currentAgent = ref('')

const progressColor = computed(() => {
  if (progress.value.failed > 0) return 'var(--neon-pink)'
  if (progress.value.percentage === 100) return 'var(--neon-green)'
  return 'var(--neon-cyan)'
})

const canProceed = computed(() => {
  return benchmarkId.value && !isRunning.value
})

const loadAgents = async () => {
  loading.value = true
  try {
    const res = await api.getAgents({ pageNum: 1, pageSize: 100 })
    if (res.data.code === 200) {
      agents.value = (res.data.data.records || []).filter(a => a.status === 1)
    }
  } catch (e) {
    ElMessage.error('加载Agent列表失败')
  } finally {
    loading.value = false
  }
}

const handleSelectionChange = (selection) => {
  selectedAgents.value = selection
}

const getStatusClass = (status) => {
  const map = { 1: 'online', 2: 'offline', 0: 'draft' }
  return map[status] || 'draft'
}

const getStatusText = (status) => {
  const map = { 1: '已上线', 2: '已下线', 0: '草稿' }
  return map[status] || '未知'
}

const handleStart = async () => {
  if (selectedAgents.value.length === 0) {
    ElMessage.warning('请选择至少一个Agent')
    return
  }

  isRunning.value = true
  progress.value = {
    completed: 0,
    running: 0,
    failed: 0,
    total: selectedAgents.value.length * config.value.sampleCount,
    percentage: 0,
    elapsed: 0
  }

  const startTime = Date.now()
  const elapsedTimer = setInterval(() => {
    progress.value.elapsed = Math.floor((Date.now() - startTime) / 1000)
  }, 1000)

  try {
    const res = await api.startBenchmark({
      datasetId: props.datasetId,
      agentIds: selectedAgents.value.map(a => a.id),
      sampleCount: config.value.sampleCount,
      timeout: config.value.timeout,
      retryCount: config.value.retryCount,
      concurrency: config.value.concurrency
    })

    if (res.data.code === 200) {
      benchmarkId.value = res.data.data.benchmarkId
      ElMessage.success('测评任务已启动')

      pollProgress()
    } else {
      ElMessage.error(res.data.message || '启动测评失败')
      isRunning.value = false
    }
  } catch (e) {
    ElMessage.error('启动测评失败')
    isRunning.value = false
  } finally {
    clearInterval(elapsedTimer)
  }
}

const pollProgress = () => {
  const pollTimer = setInterval(async () => {
    if (!isRunning.value) {
      clearInterval(pollTimer)
      return
    }

    try {
      const res = await api.getBenchmarkProgress(benchmarkId.value)
      if (res.data.code === 200) {
        const data = res.data.data
        progress.value.completed = data.completed || 0
        progress.value.running = data.running || 0
        progress.value.failed = data.failed || 0
        progress.value.total = data.total || progress.value.total
        progress.value.percentage = data.percentage || 0
        currentAgent.value = data.currentAgent || ''

        if (data.status === 'COMPLETED' || data.status === 'FAILED') {
          isRunning.value = false
          clearInterval(pollTimer)

          if (data.status === 'COMPLETED') {
            ElMessage.success('测评已完成')
          } else {
            ElMessage.error('测评失败')
          }
        }
      }
    } catch (e) {
      console.error('获取进度失败', e)
    }
  }, 2000)
}

const handleBack = () => {
  emit('back')
}

const handleProceed = () => {
  if (benchmarkId.value) {
    emit('next', { benchmarkId: benchmarkId.value })
  }
}

onMounted(() => {
  loadAgents()
})
</script>

<style scoped>
.agent-selector {
  max-width: 900px;
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

.agent-section {
  margin-bottom: 32px;
}

.agent-table {
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

.category-tag {
  display: inline-block;
  padding: 4px 10px;
  background: rgba(139, 92, 246, 0.15);
  border: 1px solid rgba(139, 92, 246, 0.3);
  border-radius: 6px;
  font-size: 12px;
  color: var(--neon-purple);
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.status-badge .status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.status-badge.online {
  background: rgba(0, 255, 136, 0.15);
  color: var(--neon-green);
}
.status-badge.online .status-dot {
  background: var(--neon-green);
}

.status-badge.offline {
  background: rgba(255, 170, 0, 0.15);
  color: var(--neon-orange);
}
.status-badge.offline .status-dot {
  background: var(--neon-orange);
}

.status-badge.draft {
  background: rgba(155, 89, 255, 0.15);
  color: var(--neon-purple);
}
.status-badge.draft .status-dot {
  background: var(--neon-purple);
}

.config-section {
  margin-bottom: 32px;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.config-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.config-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.config-hint {
  font-size: 12px;
  color: var(--text-muted);
}

.progress-section {
  margin-bottom: 32px;
}

.progress-info {
  padding: 20px;
  background: var(--glass-bg);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.progress-stats {
  display: flex;
  gap: 32px;
  margin-bottom: 16px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 12px;
  color: var(--text-muted);
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-value.error {
  color: var(--neon-pink);
}

.progress-detail {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-muted);
}

.selector-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
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

.start-btn {
  padding: 12px 32px;
  background: linear-gradient(135deg, var(--neon-cyan), var(--neon-green));
  border: none;
  color: #000;
  font-weight: 600;
  border-radius: 10px;
}

.start-btn:disabled {
  opacity: 0.5;
}

.proceed-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 32px;
  background: linear-gradient(135deg, var(--neon-cyan), var(--neon-purple));
  border: none;
  color: #000;
  font-weight: 600;
  border-radius: 10px;
}

.proceed-btn:disabled {
  opacity: 0.5;
}
</style>