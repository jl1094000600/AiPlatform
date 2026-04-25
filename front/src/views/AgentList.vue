<template>
  <div class="page-container">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">Agent管理</h2>
        <span class="total-count mono">共 {{ total }} 个Agent</span>
      </div>
      <el-button type="primary" @click="openDialog()" class="add-btn">
        <Plus class="btn-icon" /> 新增Agent
      </el-button>
    </div>

    <!-- Table Card -->
    <div class="glass-card table-card">
      <el-table
        :data="agents"
        v-loading="loading"
        stripe
        class="agents-table"
      >
        <el-table-column prop="id" label="ID" width="80" align="center">
          <template #default="{ row }">
            <span class="mono id-cell">#{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="agentName" label="名称" min-width="160">
          <template #default="{ row }">
            <div class="agent-name-cell">
              <div class="agent-icon">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                  <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.5"/>
                  <circle cx="12" cy="12" r="4" fill="currentColor"/>
                </svg>
              </div>
              <div class="agent-info">
                <span class="agent-name">{{ row.agentName }}</span>
                <span class="agent-code mono">{{ row.agentCode }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            <span class="category-tag">{{ row.category || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="apiUrl" label="接口地址" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono url-cell">{{ row.apiUrl || '-' }}</span>
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
        <el-table-column label="操作" width="320" align="center" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button size="small" @click="openDetail(row)" class="action-btn view">
                <View /> 详情
              </el-button>
              <el-button size="small" @click="openCallRecords(row)" class="action-btn records">
                <Document /> 调用记录
              </el-button>
              <el-button size="small" @click="openExecutionChain(row)" class="action-btn chain">
                <Link /> 执行链路
              </el-button>
              <el-button size="small" @click="openDialog(row)" class="action-btn edit">
                <Edit /> 编辑
              </el-button>
              <el-button
                size="small"
                @click="handlePublish(row)"
                v-if="row.status !== 1"
                class="action-btn publish"
              >
                <VideoPlay /> 发布
              </el-button>
              <el-button
                size="small"
                @click="handleOffline(row)"
                v-if="row.status === 1"
                class="action-btn offline"
              >
                <VideoPause /> 下线
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- Pagination -->
      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          @current-change="loadAgents"
          layout="total, prev, pager, next"
          :pager-count="5"
        />
      </div>
    </div>

    <!-- Dialog -->
    <el-dialog
      v-model="showDialog"
      :title="form.id ? '编辑Agent' : '新增Agent'"
      width="560px"
      class="agent-dialog"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-position="top" class="agent-form">
        <el-form-item label="Agent编码" required>
          <el-input v-model="form.agentCode" placeholder="如: text_generator" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.agentName" placeholder="如: 文本生成器" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="选择分类" style="width: 100%">
            <el-option label="文本处理" value="文本处理" />
            <el-option label="图像识别" value="图像识别" />
            <el-option label="问答系统" value="问答系统" />
            <el-option label="对话系统" value="对话系统" />
            <el-option label="语音处理" value="语音处理" />
          </el-select>
        </el-form-item>
        <el-form-item label="接口地址">
          <el-input v-model="form.apiUrl" placeholder="如: https://api.example.com/agent" />
        </el-form-item>
        <el-form-item label="调用方式">
          <el-radio-group v-model="form.httpMethod">
            <el-radio value="GET">GET</el-radio>
            <el-radio value="POST">POST</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="详细描述Agent的功能和用途..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false" class="cancel-btn">取消</el-button>
        <el-button type="primary" @click="handleSave" class="save-btn">
          {{ form.id ? '保存修改' : '创建Agent' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Agent Detail Dialog -->
    <el-dialog v-model="showDetailDialog" title="Agent详情" width="600px" class="detail-dialog">
      <div v-if="detailData" class="detail-content">
        <div class="detail-row">
          <span class="detail-label">Agent ID</span>
          <span class="detail-value mono">#{{ detailData.id }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">编码</span>
          <span class="detail-value mono">{{ detailData.agentCode }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">名称</span>
          <span class="detail-value">{{ detailData.agentName }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">分类</span>
          <span class="detail-value">{{ detailData.category || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">接口地址</span>
          <span class="detail-value mono">{{ detailData.apiUrl || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">调用方式</span>
          <span class="detail-value">{{ detailData.httpMethod || 'POST' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">状态</span>
          <span class="status-badge" :class="getStatusClass(detailData.status)">
            {{ getStatusText(detailData.status) }}
          </span>
        </div>
        <div class="detail-row">
          <span class="detail-label">描述</span>
          <span class="detail-value">{{ detailData.description || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">创建时间</span>
          <span class="detail-value mono">{{ formatTime(detailData.createTime) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">更新时间</span>
          <span class="detail-value mono">{{ formatTime(detailData.updateTime) }}</span>
        </div>
      </div>
    </el-dialog>

    <!-- Call Records Dialog -->
    <el-dialog v-model="showCallRecordsDialog" title="调用记录" width="900px" class="records-dialog">
      <div v-if="callRecordsAgent" class="dialog-header-info">
        <span>Agent: <strong>{{ callRecordsAgent.agentName }}</strong></span>
        <span class="mono">#{{ callRecordsAgent.id }}</span>
      </div>
      <el-table :data="callRecords" v-loading="callRecordsLoading" stripe max-height="400">
        <el-table-column prop="traceId" label="TraceID" width="180">
          <template #default="{ row }">
            <span class="mono trace-id">{{ row.traceId || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="响应时间" width="100" align="center">
          <template #default="{ row }">
            <span class="mono">{{ row.durationMs ? row.durationMs + 'ms' : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="statusCode" label="状态码" width="80" align="center">
          <template #default="{ row }">
            <span class="mono" :class="getStatusCodeClass(row.statusCode)">{{ row.statusCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="success" label="结果" width="80" align="center">
          <template #default="{ row }">
            <span class="result-badge" :class="row.success === 1 ? 'success' : 'failed'">
              {{ row.success === 1 ? '成功' : '失败' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="调用时间" min-width="160">
          <template #default="{ row }">
            <span class="mono">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper" v-if="callRecordsTotal > 0">
        <el-pagination
          v-model:current-page="callRecordsPage"
          v-model:page-size="callRecordsPageSize"
          :total="callRecordsTotal"
          @current-change="loadCallRecords"
          layout="total, prev, pager, next"
        />
      </div>
    </el-dialog>

    <!-- Execution Chain Dialog -->
    <el-dialog v-model="showExecutionChainDialog" title="执行链路" width="800px" class="chain-dialog">
      <div v-if="chainAgent" class="dialog-header-info">
        <span>Agent: <strong>{{ chainAgent.agentName }}</strong></span>
        <span class="mono">#{{ chainAgent.id }}</span>
      </div>
      <div class="chain-search">
        <el-input v-model="chainSearchKey" placeholder="输入 TaskID 或 SessionID" style="width: 300px">
          <template #append>
            <el-button @click="loadExecutionChain">查询</el-button>
          </template>
        </el-input>
      </div>
      <div v-if="executionChain.length > 0" class="chain-timeline">
        <div v-for="(node, index) in executionChain" :key="index" class="chain-node">
          <div class="chain-node-icon" :class="getChainNodeClass(node.status)">
            <span>{{ index + 1 }}</span>
          </div>
          <div class="chain-node-content">
            <div class="chain-node-header">
              <span class="chain-agent-name">{{ node.sourceAgentName }}</span>
              <span class="chain-arrow">-></span>
              <span class="chain-agent-name">{{ node.targetAgentName }}</span>
            </div>
            <div class="chain-node-meta">
              <span class="mono">Task: {{ node.taskId }}</span>
              <span>{{ node.taskType }}</span>
              <span>{{ node.durationMs }}ms</span>
            </div>
            <div class="chain-node-time mono">
              {{ formatTime(node.startTime) }} - {{ formatTime(node.endTime) }}
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else-if="!chainLoading" description="暂无执行链路数据" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, VideoPlay, VideoPause, View, Document, Link } from '@element-plus/icons-vue'
import api from '../api'

const agents = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const showDialog = ref(false)
const showDetailDialog = ref(false)
const showCallRecordsDialog = ref(false)
const showExecutionChainDialog = ref(false)
const detailData = ref(null)
const callRecordsAgent = ref(null)
const callRecords = ref([])
const callRecordsLoading = ref(false)
const callRecordsPage = ref(1)
const callRecordsPageSize = ref(10)
const callRecordsTotal = ref(0)
const chainAgent = ref(null)
const chainSearchKey = ref('')
const executionChain = ref([])
const chainLoading = ref(false)

const form = reactive({
  id: null,
  agentCode: '',
  agentName: '',
  category: '',
  apiUrl: '',
  httpMethod: 'POST',
  description: ''
})

const getStatusClass = (status) => {
  const map = { 1: 'online', 2: 'offline', 0: 'draft' }
  return map[status] || 'draft'
}

const getStatusText = (status) => {
  const map = { 1: '已上线', 2: '已下线', 0: '草稿' }
  return map[status] || '未知'
}

const loadAgents = async () => {
  loading.value = true
  try {
    const res = await api.getAgents({ pageNum: pageNum.value, pageSize: pageSize.value })
    if (res.data.code === 200) {
      agents.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    }
  } catch (e) {
    ElMessage.error('加载Agent列表失败')
  } finally {
    loading.value = false
  }
}

const openDialog = (row = null) => {
  if (row) {
    Object.assign(form, row)
  } else {
    Object.assign(form, {
      id: null,
      agentCode: '',
      agentName: '',
      category: '',
      apiUrl: '',
      httpMethod: 'POST',
      description: ''
    })
  }
  showDialog.value = true
}

const handleSave = async () => {
  if (!form.agentName) {
    ElMessage.warning('请填写Agent名称')
    return
  }
  try {
    const fn = form.id ? api.updateAgent : api.createAgent
    const res = await fn(form.id, form)
    if (res.data.code === 200) {
      ElMessage.success(form.id ? '修改成功' : '创建成功')
      showDialog.value = false
      loadAgents()
    } else {
      ElMessage.error(res.data.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const handlePublish = async (row) => {
  try {
    await api.publishAgent(row.id)
    ElMessage.success('发布成功')
    loadAgents()
  } catch (e) {
    ElMessage.error('发布失败')
  }
}

const handleOffline = async (row) => {
  try {
    await api.offlineAgent(row.id)
    ElMessage.success('下线成功')
    loadAgents()
  } catch (e) {
    ElMessage.error('下线失败')
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除Agent「${row.agentName}」吗？删除后将无法恢复。`,
      '删除确认',
      { type: 'warning' }
    )
    await api.deleteAgent(row.id)
    ElMessage.success('删除成功')
    loadAgents()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const openDetail = (row) => {
  detailData.value = row
  showDetailDialog.value = true
}

const openCallRecords = async (row) => {
  callRecordsAgent.value = row
  callRecordsPage.value = 1
  showCallRecordsDialog.value = true
  await loadCallRecords()
}

const loadCallRecords = async () => {
  callRecordsLoading.value = true
  try {
    const res = await api.getCallRecords({
      agentId: callRecordsAgent.value?.id,
      page: callRecordsPage.value,
      pageSize: callRecordsPageSize.value
    })
    if (res.data.code === 200) {
      callRecords.value = res.data.data.records || res.data.data || []
      callRecordsTotal.value = res.data.data.total || res.data.data.length || 0
    }
  } catch (e) {
    ElMessage.error('加载调用记录失败')
  } finally {
    callRecordsLoading.value = false
  }
}

const openExecutionChain = (row) => {
  chainAgent.value = row
  chainSearchKey.value = ''
  executionChain.value = []
  showExecutionChainDialog.value = true
}

const loadExecutionChain = async () => {
  if (!chainSearchKey.value) {
    ElMessage.warning('请输入 TaskID 或 SessionID')
    return
  }
  chainLoading.value = true
  try {
    const params = chainSearchKey.value.includes('-') || chainSearchKey.value.length > 20
      ? { sessionId: chainSearchKey.value }
      : { taskId: chainSearchKey.value }
    const res = await api.getExecutionChain(params)
    if (res.data.code === 200) {
      const data = res.data.data
      executionChain.value = data?.chain || []
    } else {
      executionChain.value = []
    }
  } catch (e) {
    ElMessage.error('加载执行链路失败')
    executionChain.value = []
  } finally {
    chainLoading.value = false
  }
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

const getStatusCodeClass = (code) => {
  if (!code) return ''
  if (code >= 200 && code < 300) return 'success'
  if (code >= 400 && code < 500) return 'client-error'
  if (code >= 500) return 'server-error'
  return ''
}

const getChainNodeClass = (status) => {
  const map = { 'COMPLETED': 'success', 'RUNNING': 'running', 'FAILED': 'failed', 'PENDING': 'pending' }
  return map[status] || 'pending'
}

onMounted(loadAgents)
</script>

<style scoped>
.page-container {
  animation: fadeInUp 0.4s ease;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: baseline;
  gap: 16px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}

.total-count {
  font-size: 13px;
  color: var(--text-muted);
}

.add-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  font-weight: 600;
}

.btn-icon {
  width: 16px;
  height: 16px;
}

.table-card {
  overflow: hidden;
}

.agents-table {
  --el-table-bg-color: transparent;
}

.id-cell {
  color: var(--text-muted);
  font-size: 12px;
}

.agent-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.agent-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(0, 240, 255, 0.2), rgba(139, 92, 246, 0.2));
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-cyan);
}

.agent-info {
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
  color: var(--accent-purple);
}

.url-cell {
  font-size: 12px;
  color: var(--text-secondary);
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
  box-shadow: 0 0 8px var(--neon-green);
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

.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.action-btn {
  padding: 6px 12px;
  font-size: 12px;
  border-radius: 8px;
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
  transition: all 0.2s ease;
}

.action-btn:hover {
  border-color: var(--neon-cyan);
  color: var(--neon-cyan);
}

.action-btn.publish {
  background: rgba(0, 255, 136, 0.1);
  border-color: rgba(0, 255, 136, 0.3);
  color: var(--neon-green);
}

.action-btn.publish:hover {
  background: rgba(0, 255, 136, 0.2);
}

.action-btn.offline {
  background: rgba(255, 170, 0, 0.1);
  border-color: rgba(255, 170, 0, 0.3);
  color: var(--neon-orange);
}

.action-btn.delete:hover {
  border-color: var(--neon-pink);
  color: var(--neon-pink);
  background: rgba(255, 107, 157, 0.1);
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: 24px 0 16px;
  border-top: 1px solid var(--border-color);
}

/* Dialog */
.agent-dialog :deep(.el-dialog) {
  border-radius: 20px;
}

.agent-form {
  padding: 8px 0;
}

.cancel-btn {
  padding: 12px 24px;
  border-radius: 10px;
}

.save-btn {
  padding: 12px 32px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--accent-cyan), var(--accent-purple));
  border: none;
  font-weight: 600;
}

/* Detail Dialog */
.detail-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-row {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.detail-label {
  width: 100px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--text-muted);
}

.detail-value {
  font-size: 14px;
  color: var(--text-primary);
  word-break: break-all;
}

/* Call Records Dialog */
.dialog-header-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: rgba(0, 240, 255, 0.05);
  border-radius: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.dialog-header-info strong {
  color: var(--accent-cyan);
}

.records-dialog .pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: 16px 0 0;
}

.trace-id {
  font-size: 12px;
  color: var(--text-muted);
}

.result-badge {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
}

.result-badge.success {
  background: rgba(16, 185, 129, 0.15);
  color: var(--accent-green);
}

.result-badge.failed {
  background: rgba(239, 68, 68, 0.15);
  color: var(--accent-red);
}

/* Execution Chain Dialog */
.chain-search {
  margin-bottom: 20px;
}

.chain-timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
  max-height: 400px;
  overflow-y: auto;
}

.chain-node {
  display: flex;
  gap: 16px;
  padding: 16px 0;
  position: relative;
}

.chain-node:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 19px;
  top: 48px;
  bottom: 0;
  width: 2px;
  background: var(--border-color);
}

.chain-node-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
  z-index: 1;
}

.chain-node-icon.success {
  background: rgba(16, 185, 129, 0.2);
  color: var(--accent-green);
  border: 2px solid var(--accent-green);
}

.chain-node-icon.running {
  background: rgba(59, 130, 246, 0.2);
  color: #3b82f6;
  border: 2px solid #3b82f6;
}

.chain-node-icon.failed {
  background: rgba(239, 68, 68, 0.2);
  color: var(--accent-red);
  border: 2px solid var(--accent-red);
}

.chain-node-icon.pending {
  background: rgba(156, 163, 175, 0.2);
  color: #9ca3af;
  border: 2px solid #9ca3af;
}

.chain-node-content {
  flex: 1;
  min-width: 0;
}

.chain-node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.chain-agent-name {
  font-weight: 600;
  color: var(--text-primary);
}

.chain-arrow {
  color: var(--text-muted);
}

.chain-node-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.chain-node-time {
  font-size: 11px;
  color: var(--text-muted);
}

/* Action buttons - view, records, chain */
.action-btn.view:hover {
  border-color: var(--accent-cyan);
  color: var(--accent-cyan);
}

.action-btn.records {
  background: rgba(139, 92, 246, 0.1);
  border-color: rgba(139, 92, 246, 0.3);
  color: var(--accent-purple);
}

.action-btn.records:hover {
  background: rgba(139, 92, 246, 0.2);
}

.action-btn.chain {
  background: rgba(245, 158, 11, 0.1);
  border-color: rgba(245, 158, 11, 0.3);
  color: var(--accent-orange);
}

.action-btn.chain:hover {
  background: rgba(245, 158, 11, 0.2);
}
</style>
