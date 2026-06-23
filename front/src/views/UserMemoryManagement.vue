<template>
  <div class="memory-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">企业记忆中心</p>
        <h2>让助手记住正确的事</h2>
        <p class="subtitle">查看、确认和遗忘助手在当前授权范围内使用的信息。每一条记忆都可追溯来源，并且可以随时更正。</p>
      </div>
      <div class="header-actions">
        <el-button @click="loadAll">刷新</el-button>
        <el-button type="primary" @click="activeTab = 'policy'">记忆策略</el-button>
      </div>
    </header>

    <section class="trust-bar">
      <el-icon><Lock /></el-icon>
      <span>当前仅展示你有权查看的记忆；敏感信息会先脱敏并进入确认流程，不会直接用于回答。</span>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <span>已确认</span>
        <strong>{{ summary.active }}</strong>
        <small>可以被助手参考</small>
      </article>
      <article class="metric-card pending">
        <span>待确认</span>
        <strong>{{ summary.pending }}</strong>
        <small>需要人工复核后启用</small>
      </article>
      <article class="metric-card muted-card">
        <span>不再参考</span>
        <strong>{{ summary.forgotten }}</strong>
        <small>保留审计痕迹，不参与调用</small>
      </article>
      <article class="metric-card trace-card">
        <span>当前策略</span>
        <strong>{{ recallModeLabel }}</strong>
        <small>{{ policy.enabled ? '已启用记忆能力' : '暂不使用记忆' }}</small>
      </article>
    </section>

    <el-tabs v-model="activeTab" class="memory-tabs">
      <el-tab-pane label="我的记忆" name="memories">
        <div class="toolbar">
          <el-radio-group v-model="filters.status" @change="loadMemories">
            <el-radio-button label="">全部</el-radio-button>
            <el-radio-button label="ACTIVE">已确认</el-radio-button>
            <el-radio-button label="PENDING_REVIEW">待确认</el-radio-button>
            <el-radio-button label="FORGOTTEN">不再参考</el-radio-button>
          </el-radio-group>
          <el-select v-model="filters.memoryType" clearable placeholder="全部类型" @change="loadMemories">
            <el-option label="偏好" value="PREFERENCE" />
            <el-option label="约束" value="CONSTRAINT" />
            <el-option label="决策" value="DECISION" />
            <el-option label="事实" value="FACT" />
          </el-select>
        </div>

        <section class="memory-board" v-loading="loading">
          <el-empty v-if="memories.length === 0" description="还没有可展示的记忆。完成一次项目协作后，助手会将候选信息送来确认。" />
          <article v-for="memory in memories" :key="memory.id" class="memory-card">
            <div class="memory-card-main">
              <div class="memory-tags">
                <el-tag size="small" :type="statusTagType(memory.status)">{{ statusLabel(memory.status) }}</el-tag>
                <el-tag size="small" effect="plain">{{ typeLabel(memory.memoryType) }}</el-tag>
                <el-tag v-if="memory.sensitivity !== 'PUBLIC'" size="small" type="warning" effect="plain">{{ sensitivityLabel(memory.sensitivity) }}</el-tag>
              </div>
              <h3>{{ memory.title }}</h3>
              <p>{{ memory.content }}</p>
              <div class="memory-foot">
                <span>来源：{{ sourceLabel(memory.sourceType) }}</span>
                <span>更新于 {{ formatTime(memory.updateTime) }}</span>
                <span>版本 {{ memory.version || 1 }}</span>
              </div>
            </div>
            <div class="memory-actions">
              <el-button link type="primary" @click="openDetail(memory)">查看与编辑</el-button>
              <el-button v-if="memory.status === 'PENDING_REVIEW'" link type="success" @click="confirm(memory)">确认启用</el-button>
              <el-button v-if="memory.status !== 'FORGOTTEN'" link type="danger" @click="forget(memory)">不再参考</el-button>
            </div>
          </article>
        </section>
        <el-pagination
          v-if="page.total > page.pageSize"
          class="pager"
          layout="prev, pager, next"
          :total="page.total"
          :page-size="page.pageSize"
          v-model:current-page="page.pageNum"
          @current-change="loadMemories"
        />
      </el-tab-pane>

      <el-tab-pane label="记忆策略" name="policy">
        <section class="policy-panel" v-loading="policyLoading">
          <div class="policy-intro">
            <h3>使用边界</h3>
            <p>策略决定助手是否参考记忆、保留多久，以及每次调用可使用多少上下文。建议先以“仅审计”观察效果，再逐步启用。</p>
          </div>
          <el-form label-position="top" class="policy-form">
            <el-form-item label="启用记忆">
              <el-switch v-model="policy.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="关闭" />
            </el-form-item>
            <el-form-item label="调用方式">
              <el-radio-group v-model="policy.recallMode">
                <el-radio label="OFF">关闭</el-radio>
                <el-radio label="AUDIT">仅审计</el-radio>
                <el-radio label="CANARY">灰度验证（10%）</el-radio>
                <el-radio label="ENFORCED">正式启用</el-radio>
              </el-radio-group>
            </el-form-item>
            <div class="budget-grid">
              <el-form-item label="保留天数"><el-input-number v-model="policy.retentionDays" :min="1" :max="3650" /></el-form-item>
              <el-form-item label="会话 Token 预算"><el-input-number v-model="policy.sessionTokenBudget" :min="0" :max="16000" :step="100" /></el-form-item>
              <el-form-item label="工作记忆 Token 预算"><el-input-number v-model="policy.workingTokenBudget" :min="0" :max="16000" :step="100" /></el-form-item>
              <el-form-item label="长期记忆 Token 预算"><el-input-number v-model="policy.longTermTokenBudget" :min="0" :max="16000" :step="100" /></el-form-item>
            </div>
            <el-button type="primary" :loading="savingPolicy" @click="savePolicy">保存策略</el-button>
          </el-form>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="drawerVisible" title="记忆详情" size="min(560px, 92vw)">
      <template v-if="selectedMemory">
        <div class="drawer-meta">
          <el-tag :type="statusTagType(selectedMemory.status)">{{ statusLabel(selectedMemory.status) }}</el-tag>
          <span>来源：{{ sourceLabel(selectedMemory.sourceType) }}</span>
        </div>
        <el-alert v-if="selectedMemory.sensitivity !== 'PUBLIC'" title="此记忆包含受控内容，编辑时请勿补充个人敏感信息。" type="warning" :closable="false" show-icon />
        <el-form label-position="top" class="edit-form">
          <el-form-item label="标题"><el-input v-model="editing.title" maxlength="120" show-word-limit /></el-form-item>
          <el-form-item label="内容"><el-input v-model="editing.content" type="textarea" :rows="7" maxlength="2000" show-word-limit /></el-form-item>
          <el-form-item label="修改说明（可选）"><el-input v-model="editing.reason" placeholder="例如：客户已确认最新口径" /></el-form-item>
        </el-form>
        <div class="version-title">历史版本</div>
        <el-timeline v-if="versions.length" class="version-list">
          <el-timeline-item v-for="version in versions" :key="version.id" :timestamp="formatTime(version.createTime)">
            版本 {{ version.version }} · {{ version.changeReason || '内容更新' }}
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无历史版本" :image-size="70" />
      </template>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingMemory" :disabled="selectedMemory?.status === 'FORGOTTEN'" @click="saveMemory">保存修改</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import api from '../api'

const activeTab = ref('memories')
const loading = ref(false)
const policyLoading = ref(false)
const savingPolicy = ref(false)
const savingMemory = ref(false)
const memories = ref([])
const selectedMemory = ref(null)
const versions = ref([])
const drawerVisible = ref(false)
const filters = reactive({ status: '', memoryType: '' })
const page = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const policy = reactive({ enabled: 0, recallMode: 'AUDIT', retentionDays: 365, sessionTokenBudget: 800, workingTokenBudget: 800, longTermTokenBudget: 1200 })
const editing = reactive({ title: '', content: '', reason: '' })

const summary = computed(() => memories.value.reduce((acc, item) => {
  if (item.status === 'ACTIVE') acc.active += 1
  if (item.status === 'PENDING_REVIEW') acc.pending += 1
  if (item.status === 'FORGOTTEN') acc.forgotten += 1
  return acc
}, { active: 0, pending: 0, forgotten: 0 }))

const recallModeLabel = computed(() => ({ OFF: '关闭', AUDIT: '仅审计', CANARY: '灰度验证', ENFORCED: '正式启用' }[policy.recallMode] || '仅审计'))

const loadMemories = async () => {
  loading.value = true
  try {
    const res = await api.getMemories({ pageNum: page.pageNum, pageSize: page.pageSize, ...filters })
    const data = res.data.data || {}
    memories.value = data.records || []
    page.total = data.total || 0
  } catch {
    ElMessage.error('记忆列表加载失败')
  } finally {
    loading.value = false
  }
}

const loadPolicy = async () => {
  policyLoading.value = true
  try {
    const res = await api.getEffectiveMemoryPolicy()
    Object.assign(policy, res.data.data || {})
  } catch {
    ElMessage.error('记忆策略加载失败')
  } finally {
    policyLoading.value = false
  }
}

const loadAll = () => Promise.all([loadMemories(), loadPolicy()])

const openDetail = async memory => {
  try {
    const res = await api.getMemory(memory.id)
    selectedMemory.value = res.data.data
    Object.assign(editing, { title: selectedMemory.value.title, content: selectedMemory.value.content, reason: '' })
    const versionRes = await api.getMemoryVersions(memory.id)
    versions.value = versionRes.data.data || []
    drawerVisible.value = true
  } catch {
    ElMessage.error('记忆详情加载失败')
  }
}

const saveMemory = async () => {
  savingMemory.value = true
  try {
    await api.updateMemory(selectedMemory.value.id, { version: selectedMemory.value.version, ...editing })
    ElMessage.success('记忆已更新，并保留了版本记录')
    drawerVisible.value = false
    await loadMemories()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败，可能已被其他人更新')
  } finally {
    savingMemory.value = false
  }
}

const forget = async memory => {
  try {
    const { value } = await ElMessageBox.prompt('请说明不再参考这条记忆的原因（可选）', '确认遗忘', { confirmButtonText: '确认不再参考', cancelButtonText: '取消', inputPlaceholder: '例如：客户情况已变化' })
    await api.forgetMemory(memory.id, { reason: value })
    ElMessage.success('已停止参考这条记忆')
    await loadMemories()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('遗忘操作失败')
  }
}

const confirm = async memory => {
  try {
    await ElMessageBox.confirm('确认后，这条记忆将可以被助手参考。', '确认启用', { confirmButtonText: '确认启用', cancelButtonText: '取消', type: 'info' })
    await api.confirmMemory(memory.id)
    ElMessage.success('记忆已确认启用')
    await loadMemories()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('确认操作失败')
  }
}

const savePolicy = async () => {
  savingPolicy.value = true
  try {
    await api.saveMemoryPolicy({ ...policy, scopeType: policy.scopeType || 'TENANT', scopeKey: policy.scopeKey || 'default' })
    ElMessage.success('记忆策略已保存')
    await loadPolicy()
  } catch {
    ElMessage.error('策略保存失败')
  } finally {
    savingPolicy.value = false
  }
}

const statusLabel = status => ({ ACTIVE: '已确认', PENDING_REVIEW: '待确认', FORGOTTEN: '不再参考', EXPIRED: '已过期' }[status] || status)
const statusTagType = status => ({ ACTIVE: 'success', PENDING_REVIEW: 'warning', FORGOTTEN: 'info', EXPIRED: 'info' }[status] || '')
const typeLabel = type => ({ PREFERENCE: '偏好', CONSTRAINT: '约束', DECISION: '决策', FACT: '事实' }[type] || type || '记忆')
const sensitivityLabel = value => ({ INTERNAL: '内部', CONFIDENTIAL: '保密', PII: '个人信息' }[value] || value)
const sourceLabel = value => ({ PIPELINE: '项目交付', MANUAL: '人工维护', MIGRATION: '历史迁移', AGENT: '智能助手', WORKFLOW: '工作流' }[value] || value || '系统')
const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 16) : '-'

onMounted(loadAll)
</script>

<style scoped>
.memory-page { max-width: 1440px; padding: 28px; color: var(--text-primary); }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 24px; margin-bottom: 18px; }
.eyebrow { margin: 0 0 8px; color: var(--accent-cyan); font-size: 13px; font-weight: 700; letter-spacing: .08em; }
.page-header h2 { margin: 0; font-size: 30px; letter-spacing: -.02em; }
.subtitle { max-width: 720px; margin: 10px 0 0; color: var(--text-muted); line-height: 1.65; }
.header-actions { display: flex; flex-shrink: 0; gap: 10px; }
.trust-bar { display: flex; align-items: center; gap: 9px; padding: 11px 14px; border: 1px solid rgba(34, 211, 238, .25); border-radius: 8px; background: rgba(8, 145, 178, .08); color: var(--text-secondary); font-size: 13px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin: 18px 0 24px; }
.metric-card { min-height: 116px; padding: 18px; border: 1px solid var(--glass-border); border-radius: 10px; background: var(--glass-bg); }
.metric-card span, .metric-card small { display: block; color: var(--text-muted); }
.metric-card strong { display: block; margin: 8px 0 5px; color: #34d399; font-size: 28px; }
.metric-card.pending strong { color: #fbbf24; }.metric-card.muted-card strong { color: #94a3b8; }.metric-card.trace-card strong { color: var(--accent-cyan); font-size: 21px; }
.memory-tabs :deep(.el-tabs__header) { margin-bottom: 18px; }
.toolbar { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.toolbar .el-select { width: 160px; }
.memory-board { min-height: 260px; }
.memory-card { display: flex; justify-content: space-between; gap: 20px; padding: 18px 0; border-bottom: 1px solid var(--border-color); }
.memory-card:first-child { border-top: 1px solid var(--border-color); }
.memory-card-main { min-width: 0; }.memory-tags { display: flex; gap: 7px; margin-bottom: 8px; }.memory-card h3 { margin: 0; font-size: 16px; }.memory-card p { margin: 8px 0; color: var(--text-secondary); line-height: 1.65; white-space: pre-wrap; }.memory-foot { display: flex; flex-wrap: wrap; gap: 14px; color: var(--text-muted); font-size: 12px; }.memory-actions { display: flex; align-self: center; flex-shrink: 0; gap: 6px; }.pager { margin-top: 18px; justify-content: flex-end; }
.policy-panel { display: grid; grid-template-columns: minmax(240px, .7fr) minmax(420px, 1.3fr); gap: 32px; padding: 24px; border: 1px solid var(--glass-border); border-radius: 10px; background: var(--glass-bg); }.policy-intro h3 { margin: 0 0 8px; }.policy-intro p { color: var(--text-muted); line-height: 1.7; }.budget-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 18px; }.drawer-meta { display: flex; gap: 10px; align-items: center; margin-bottom: 16px; color: var(--text-muted); font-size: 13px; }.edit-form { margin-top: 18px; }.version-title { margin-top: 22px; font-weight: 700; }.version-list { margin-top: 14px; }
@media (max-width: 1000px) { .metric-grid { grid-template-columns: 1fr 1fr; }.policy-panel { grid-template-columns: 1fr; gap: 12px; } }
@media (max-width: 720px) { .memory-page { padding: 18px; }.page-header, .toolbar, .memory-card { flex-direction: column; }.header-actions, .memory-actions { align-self: stretch; }.metric-grid, .budget-grid { grid-template-columns: 1fr; }.toolbar .el-select { width: 100%; }.memory-actions { align-self: flex-start; } }
</style>
