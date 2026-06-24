<template>
  <div class="run-console">
    <section class="page-header">
      <div>
        <h2>运行控制台</h2>
        <p>查看项目 Agent 的执行状态、引用约定与交付物。敏感输入、Prompt 和内部日志不会在此展示。</p>
      </div>
      <el-button :loading="loading" type="primary" @click="loadRuns">刷新</el-button>
    </section>

    <section class="filters panel">
      <span>状态</span>
      <el-select v-model="filters.status" clearable placeholder="全部状态" @change="loadRuns">
        <el-option v-for="status in statuses" :key="status" :label="statusLabel(status)" :value="status" />
      </el-select>
      <span class="run-count">共 {{ total }} 条运行记录</span>
    </section>

    <div class="workspace">
      <section class="panel run-list">
        <el-table v-loading="loading" :data="runs" highlight-current-row @current-change="selectRun">
          <el-table-column prop="id" label="Run" width="90" />
          <el-table-column prop="businessType" label="业务" min-width="120" show-overflow-tooltip />
          <el-table-column prop="projectKey" label="项目" min-width="130" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="112">
            <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && runs.length === 0" description="当前筛选条件下没有可查看的运行记录" />
        <div class="pager" v-if="total > pageSize">
          <el-pagination v-model:current-page="filters.pageNum" :page-size="pageSize" layout="prev, pager, next" :total="total" @current-change="loadRuns" />
        </div>
      </section>

      <section class="panel detail" v-loading="detailLoading">
        <el-empty v-if="!selected" description="从左侧选择一条运行记录查看详情" />
        <template v-else>
          <div class="detail-title">
            <div>
              <div class="eyebrow">RUN #{{ selected.run.id }}</div>
              <h3>{{ selected.run.businessType }} · {{ selected.run.businessId }}</h3>
              <p>{{ selected.run.projectKey }} · Agent {{ selected.run.agentId }} / v{{ selected.run.agentVersionId }}</p>
            </div>
            <div class="actions">
              <el-tag :type="statusType(selected.run.status)" size="large">{{ statusLabel(selected.run.status) }}</el-tag>
              <el-button v-if="canCancel(selected.run.status)" type="danger" plain @click="confirmCancel">取消运行</el-button>
            </div>
          </div>

          <div class="metrics">
            <div><span>创建</span><strong>{{ formatTime(selected.run.createTime) }}</strong></div>
            <div><span>开始</span><strong>{{ formatTime(selected.run.startTime) }}</strong></div>
            <div><span>结束</span><strong>{{ formatTime(selected.run.endTime) }}</strong></div>
            <div><span>累计 Token</span><strong>{{ selected.run.totalTokens ?? 0 }}</strong></div>
          </div>
          <el-alert v-if="selected.run.errorMessage" :title="selected.run.errorMessage" type="warning" :closable="false" show-icon />

          <el-collapse v-model="openPanels">
            <el-collapse-item name="steps" title="执行进度与任务">
              <h4>步骤</h4>
              <el-table :data="selected.steps" size="small">
                <el-table-column prop="stepNo" label="#" width="58" />
                <el-table-column prop="stepType" label="业务动作" min-width="120" />
                <el-table-column prop="status" label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
                <el-table-column label="Token" width="100"><template #default="{ row }">{{ row.inputTokens || 0 }} / {{ row.outputTokens || 0 }}</template></el-table-column>
                <el-table-column prop="errorMessage" label="说明" min-width="170" show-overflow-tooltip />
              </el-table>
              <h4>任务</h4>
              <el-table :data="selected.tasks" size="small">
                <el-table-column prop="id" label="任务" width="70" />
                <el-table-column prop="taskType" label="类型" min-width="110" />
                <el-table-column prop="status" label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
                <el-table-column label="尝试" width="80"><template #default="{ row }">{{ row.attemptCount }} / {{ row.maxAttempts }}</template></el-table-column>
                <el-table-column prop="errorMessage" label="说明" min-width="160" show-overflow-tooltip />
              </el-table>
            </el-collapse-item>

            <el-collapse-item name="memory" title="本次参考的项目约定">
              <el-alert v-if="selected.memoryReferenceNotice" :title="selected.memoryReferenceNotice" type="info" :closable="false" show-icon />
              <el-table v-else :data="selected.memorySnapshots" size="small">
                <el-table-column prop="memoryCode" label="记忆编号" min-width="160" />
                <el-table-column prop="sourceType" label="来源" min-width="100" />
                <el-table-column prop="scopeType" label="范围" min-width="100" />
                <el-table-column prop="memoryVersion" label="版本" width="80" />
                <el-table-column prop="tokenCount" label="Token" width="90" />
                <el-table-column prop="policyVersion" label="策略版本" width="100" />
              </el-table>
            </el-collapse-item>

            <el-collapse-item name="artifacts" title="交付物">
              <el-table :data="selected.artifacts" size="small">
                <el-table-column prop="id" label="编号" width="80" />
                <el-table-column prop="artifactType" label="类型" min-width="120" />
                <el-table-column prop="title" label="安全标题" min-width="160" />
                <el-table-column prop="status" label="状态" width="110" />
                <el-table-column prop="updateTime" label="更新时间" min-width="165"><template #default="{ row }">{{ formatTime(row.updateTime) }}</template></el-table-column>
              </el-table>
              <el-empty v-if="selected.artifacts.length === 0" description="尚未生成交付物" :image-size="70" />
            </el-collapse-item>
          </el-collapse>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const loading = ref(false)
const detailLoading = ref(false)
const runs = ref([])
const selected = ref(null)
const total = ref(0)
const pageSize = 20
const filters = ref({ pageNum: 1, status: '' })
const openPanels = ref(['steps', 'memory', 'artifacts'])
const statuses = ['QUEUED', 'RUNNING', 'WAITING_APPROVAL', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMEOUT']

const loadRuns = async () => {
  loading.value = true
  try {
    const response = await api.getAgentRuns({ pageNum: filters.value.pageNum, pageSize, status: filters.value.status || undefined })
    const page = response.data.data || {}
    runs.value = page.records || []
    total.value = page.total || 0
    if (runs.value.length && !runs.value.some(item => item.id === selected.value?.run?.id)) await loadDetail(runs.value[0].id)
    if (!runs.value.length) selected.value = null
  } catch {
    ElMessage.error('运行记录加载失败')
  } finally {
    loading.value = false
  }
}

const selectRun = (row) => { if (row) loadDetail(row.id) }

const loadDetail = async (id) => {
  detailLoading.value = true
  try {
    const response = await api.getAgentRunDetail(id)
    selected.value = response.data.data
  } catch {
    ElMessage.error('运行详情加载失败或你已无权查看')
  } finally {
    detailLoading.value = false
  }
}

const confirmCancel = async () => {
  try {
    await ElMessageBox.confirm('取消后，尚未完成的任务将停止；该操作不可撤销。', '确认取消运行', { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '返回' })
    await api.cancelAgentRun(selected.value.run.id, 'Cancelled by an authorized user')
    ElMessage.success('运行已取消')
    await loadRuns()
    await loadDetail(selected.value.run.id)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('取消失败，请稍后重试')
  }
}

const canCancel = (status) => ['QUEUED', 'RUNNING', 'WAITING_APPROVAL'].includes(status)
const statusLabel = (status) => ({ QUEUED: '排队中', RUNNING: '运行中', WAITING_APPROVAL: '等待确认', SUCCEEDED: '已完成', FAILED: '失败', CANCELLED: '已取消', TIMEOUT: '已超时' })[status] || status
const statusType = (status) => ({ QUEUED: 'info', RUNNING: 'primary', WAITING_APPROVAL: 'warning', SUCCEEDED: 'success', FAILED: 'danger', CANCELLED: 'info', TIMEOUT: 'danger' })[status] || 'info'
const formatTime = (value) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'

onMounted(loadRuns)
</script>

<style scoped>
.run-console { color: var(--text-primary); }
.page-header { display: flex; justify-content: space-between; gap: 24px; align-items: flex-start; margin-bottom: 18px; }
.page-header h2 { margin: 0 0 8px; font-size: 24px; }
.page-header p { margin: 0; color: var(--text-muted); }
.panel { border: 1px solid var(--glass-border); border-radius: 10px; background: var(--glass-bg); }
.filters { display: flex; align-items: center; gap: 12px; padding: 12px 16px; margin-bottom: 16px; }
.filters .el-select { width: 180px; }
.run-count { margin-left: auto; color: var(--text-muted); font-size: 13px; }
.workspace { display: grid; grid-template-columns: minmax(430px, .95fr) minmax(520px, 1.35fr); gap: 16px; align-items: start; }
.run-list { overflow: hidden; }
.pager { padding: 12px; display: flex; justify-content: flex-end; }
.detail { padding: 18px; min-height: 520px; }
.detail-title { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.eyebrow { color: var(--accent-cyan); font-size: 12px; font-weight: 700; letter-spacing: .08em; }
.detail-title h3 { margin: 5px 0; font-size: 20px; }
.detail-title p { color: var(--text-muted); margin: 0; font-size: 13px; }
.actions { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin: 16px 0; }
.metrics div { padding: 12px; border-radius: 8px; background: rgba(148, 163, 184, .08); display: flex; flex-direction: column; gap: 6px; }
.metrics span { font-size: 12px; color: var(--text-muted); }
.metrics strong { font-size: 13px; word-break: break-word; }
h4 { margin: 12px 0 8px; font-size: 14px; }
:deep(.el-collapse-item__header) { font-weight: 700; }
@media (max-width: 1200px) { .workspace { grid-template-columns: 1fr; } }
@media (max-width: 700px) { .page-header, .detail-title { flex-direction: column; } .metrics { grid-template-columns: repeat(2, 1fr); } .run-count { display: none; } }
</style>
