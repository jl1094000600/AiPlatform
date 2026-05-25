<template>
  <div class="governance-page">
    <section class="page-hero">
      <div>
        <p class="eyebrow">AI Output Governance</p>
        <h1>AI 产出治理</h1>
        <p class="subtitle">
          追踪流水线中由模型生成的 PRD、代码和质量评估结果，沉淀模型、风险、门禁和产物审计线索。
        </p>
      </div>
      <div class="hero-metrics">
        <div class="metric-card">
          <span>治理记录</span>
          <strong>{{ total }}</strong>
        </div>
        <div class="metric-card">
          <span>高风险</span>
          <strong>{{ riskCount.HIGH }}</strong>
        </div>
        <div class="metric-card">
          <span>已阻塞</span>
          <strong>{{ statusCount.BLOCKED }}</strong>
        </div>
      </div>
    </section>

    <section class="toolbar">
      <el-input
        v-model="filters.pipelineId"
        clearable
        placeholder="流水线 ID"
        class="filter-input"
        @keyup.enter="loadRecords"
      />
      <el-select v-model="filters.artifactType" clearable placeholder="产物类型" class="filter-select">
        <el-option label="PRD" value="PRD" />
        <el-option label="代码" value="CODE" />
        <el-option label="代码质量评估" value="CODE_QUALITY" />
      </el-select>
      <el-select v-model="filters.riskLevel" clearable placeholder="风险等级" class="filter-select">
        <el-option label="低风险" value="LOW" />
        <el-option label="中风险" value="MEDIUM" />
        <el-option label="高风险" value="HIGH" />
      </el-select>
      <el-select v-model="filters.governanceStatus" clearable placeholder="治理状态" class="filter-select">
        <el-option label="待复核" value="NEEDS_REVIEW" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="已阻塞" value="BLOCKED" />
      </el-select>
      <el-button type="primary" @click="loadRecords">刷新</el-button>
    </section>

    <section class="records-panel">
      <el-table v-loading="loading" :data="records" row-key="id" @row-click="openDetail">
        <el-table-column prop="recordCode" label="记录编号" min-width="170" />
        <el-table-column label="产物" width="130">
          <template #default="{ row }">
            <el-tag effect="plain">{{ artifactLabel(row.artifactType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pipelineId" label="流水线" width="100" />
        <el-table-column prop="stageKey" label="阶段" min-width="160" />
        <el-table-column prop="modelCode" label="模型" min-width="140" />
        <el-table-column label="风险" width="120">
          <template #default="{ row }">
            <el-tag :type="riskTag(row.riskLevel)">{{ riskLabel(row.riskLevel) }} {{ row.riskScore || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.governanceStatus)">
              {{ statusLabel(row.governanceStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="生成时间" width="180" />
      </el-table>

      <div class="pagination-row">
        <el-pagination
          layout="prev, pager, next, total"
          :total="total"
          :page-size="page.pageSize"
          v-model:current-page="page.pageNum"
          @current-change="loadRecords"
        />
      </div>
    </section>

    <section class="records-panel">
      <div class="section-header">
        <div>
          <h2>策略模板</h2>
          <p>用于把 AI 产物风险转成统一 Gate，例如敏感信息、越权工具调用、未测代码和高风险输出。</p>
        </div>
        <el-button type="primary" @click="openPolicyDialog()">新增策略</el-button>
      </div>
      <el-table v-loading="policyLoading" :data="policies" row-key="id">
        <el-table-column prop="policyCode" label="编码" min-width="170" />
        <el-table-column prop="policyName" label="策略名称" min-width="160" />
        <el-table-column label="目标" width="110">
          <template #default="{ row }">{{ artifactLabel(row.targetArtifactType) }}</template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="130" />
        <el-table-column label="严重级别" width="110">
          <template #default="{ row }">
            <el-tag :type="severityTag(row.severity)">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="命中阻塞" width="100">
          <template #default="{ row }">
            <el-tag :type="row.blockOnMatch === 1 ? 'danger' : 'warning'">
              {{ row.blockOnMatch === 1 ? '阻塞' : '提醒' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openPolicyDialog(row)">编辑</el-button>
            <el-button link type="danger" @click.stop="deletePolicy(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="drawer.visible" size="520px" title="治理记录详情">
      <div v-if="drawer.record" class="detail-stack">
        <div class="detail-header">
          <h2>{{ artifactLabel(drawer.record.artifactType) }}</h2>
          <el-tag :type="statusTag(drawer.record.governanceStatus)">
            {{ statusLabel(drawer.record.governanceStatus) }}
          </el-tag>
        </div>
        <div class="risk-bar">
          <span :style="{ width: (drawer.record.riskScore || 0) + '%' }"></span>
        </div>
        <dl class="detail-list">
          <div>
            <dt>记录编号</dt>
            <dd>{{ drawer.record.recordCode }}</dd>
          </div>
          <div>
            <dt>流水线 / 阶段</dt>
            <dd>#{{ drawer.record.pipelineId || '-' }} / {{ drawer.record.stageKey || '-' }}</dd>
          </div>
          <div>
            <dt>模型</dt>
            <dd>{{ drawer.record.modelCode || '-' }}</dd>
          </div>
          <div>
            <dt>Token</dt>
            <dd>{{ drawer.record.totalTokens || 0 }} total</dd>
          </div>
          <div>
            <dt>产物路径</dt>
            <dd class="path-text">{{ drawer.record.artifactPath || '-' }}</dd>
          </div>
          <div>
            <dt>摘要</dt>
            <dd>{{ drawer.record.artifactSummary || '暂无摘要' }}</dd>
          </div>
        </dl>
        <div class="json-block">
          <h3>治理快照</h3>
          <pre>{{ prettyJson(drawer.record.policySnapshot) }}</pre>
        </div>
        <div class="json-block">
          <h3>元数据</h3>
          <pre>{{ prettyJson(drawer.record.metadataJson) }}</pre>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="policyDialog.visible" :title="policyDialog.form.id ? '编辑策略模板' : '新增策略模板'" width="620px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="策略编码">
            <el-input v-model="policyDialog.form.policyCode" placeholder="例如 SECRET_SCAN" />
          </el-form-item>
          <el-form-item label="策略名称">
            <el-input v-model="policyDialog.form.policyName" placeholder="例如 敏感信息拦截" />
          </el-form-item>
          <el-form-item label="目标产物">
            <el-select v-model="policyDialog.form.targetArtifactType">
              <el-option label="任意产物" value="ANY" />
              <el-option label="PRD" value="PRD" />
              <el-option label="代码" value="CODE" />
              <el-option label="代码质量评估" value="CODE_QUALITY" />
            </el-select>
          </el-form-item>
          <el-form-item label="检测方式">
            <el-select v-model="policyDialog.form.detectorType">
              <el-option label="正则检测" value="REGEX" />
              <el-option label="关键词检测" value="KEYWORD" />
              <el-option label="缺少测试" value="MISSING_TEST" />
              <el-option label="高风险输出" value="HIGH_RISK" />
            </el-select>
          </el-form-item>
          <el-form-item label="分类">
            <el-input v-model="policyDialog.form.category" placeholder="security / tool_permission / testability" />
          </el-form-item>
          <el-form-item label="严重级别">
            <el-select v-model="policyDialog.form.severity">
              <el-option label="BLOCKER" value="BLOCKER" />
              <el-option label="CRITICAL" value="CRITICAL" />
              <el-option label="MAJOR" value="MAJOR" />
              <el-option label="MINOR" value="MINOR" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="说明">
          <el-input v-model="policyDialog.form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="策略配置 JSON">
          <el-input v-model="policyDialog.form.configJson" type="textarea" :rows="5" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="命中后处理">
            <el-switch
              v-model="policyDialog.form.blockOnMatch"
              :active-value="1"
              :inactive-value="0"
              active-text="阻塞"
              inactive-text="提醒"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch
              v-model="policyDialog.form.status"
              :active-value="1"
              :inactive-value="0"
              active-text="启用"
              inactive-text="停用"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="policyDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="savePolicy">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const loading = ref(false)
const records = ref([])
const policies = ref([])
const total = ref(0)
const policyLoading = ref(false)
const page = reactive({ pageNum: 1, pageSize: 20 })
const filters = reactive({
  pipelineId: '',
  artifactType: '',
  riskLevel: '',
  governanceStatus: ''
})
const drawer = reactive({
  visible: false,
  record: null
})
const policyDialog = reactive({
  visible: false,
  form: defaultPolicyForm()
})

const riskCount = computed(() => countBy('riskLevel'))
const statusCount = computed(() => countBy('governanceStatus'))

function countBy(field) {
  return records.value.reduce((acc, item) => {
    acc[item[field]] = (acc[item[field]] || 0) + 1
    return acc
  }, { HIGH: 0, MEDIUM: 0, LOW: 0, BLOCKED: 0 })
}

async function loadRecords() {
  loading.value = true
  try {
    const params = {
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      artifactType: filters.artifactType || undefined,
      riskLevel: filters.riskLevel || undefined,
      governanceStatus: filters.governanceStatus || undefined
    }
    if (filters.pipelineId) {
      params.pipelineId = Number(filters.pipelineId)
    }
    const res = await api.getAiOutputGovernanceRecords(params)
    const data = res.data?.data || {}
    records.value = data.records || []
    total.value = Number(data.total || 0)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载 AI 产出治理记录失败')
  } finally {
    loading.value = false
  }
}

async function loadPolicies() {
  policyLoading.value = true
  try {
    const res = await api.getAiOutputGovernancePolicyTemplates({ pageNum: 1, pageSize: 50 })
    policies.value = res.data?.data?.records || []
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载治理策略模板失败')
  } finally {
    policyLoading.value = false
  }
}

async function openDetail(row) {
  try {
    const res = await api.getAiOutputGovernanceRecord(row.id)
    drawer.record = res.data?.data || row
    drawer.visible = true
  } catch (error) {
    drawer.record = row
    drawer.visible = true
  }
}

function artifactLabel(value) {
  return {
    ANY: '任意产物',
    PRD: 'PRD',
    CODE: '代码',
    CODE_QUALITY: '质量评估'
  }[value] || value || '-'
}

function severityTag(value) {
  return {
    BLOCKER: 'danger',
    CRITICAL: 'danger',
    MAJOR: 'warning',
    MINOR: 'info'
  }[value] || 'info'
}

function defaultPolicyForm() {
  return {
    id: null,
    policyCode: '',
    policyName: '',
    description: '',
    category: 'security',
    severity: 'MAJOR',
    targetArtifactType: 'CODE',
    detectorType: 'REGEX',
    configJson: '{}',
    blockOnMatch: 1,
    status: 1
  }
}

function openPolicyDialog(row) {
  policyDialog.form = row ? { ...row } : defaultPolicyForm()
  policyDialog.visible = true
}

async function savePolicy() {
  try {
    JSON.parse(policyDialog.form.configJson || '{}')
  } catch {
    ElMessage.error('策略配置必须是合法 JSON')
    return
  }
  try {
    if (policyDialog.form.id) {
      await api.updateAiOutputGovernancePolicyTemplate(policyDialog.form.id, policyDialog.form)
    } else {
      await api.createAiOutputGovernancePolicyTemplate(policyDialog.form)
    }
    ElMessage.success('策略模板已保存')
    policyDialog.visible = false
    loadPolicies()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存策略模板失败')
  }
}

async function deletePolicy(row) {
  try {
    await ElMessageBox.confirm(`确定删除策略「${row.policyName}」吗？`, '删除确认', { type: 'warning' })
    await api.deleteAiOutputGovernancePolicyTemplate(row.id)
    ElMessage.success('策略模板已删除')
    loadPolicies()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '删除策略模板失败')
    }
  }
}

function riskLabel(value) {
  return {
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险'
  }[value] || value || '-'
}

function riskTag(value) {
  return {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger'
  }[value] || 'info'
}

function statusLabel(value) {
  return {
    NEEDS_REVIEW: '待复核',
    APPROVED: '已通过',
    BLOCKED: '已阻塞'
  }[value] || value || '-'
}

function statusTag(value) {
  return {
    NEEDS_REVIEW: 'warning',
    APPROVED: 'success',
    BLOCKED: 'danger'
  }[value] || 'info'
}

function prettyJson(value) {
  if (!value) {
    return '{}'
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

onMounted(() => {
  loadRecords()
  loadPolicies()
})
</script>

<style scoped>
.governance-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  color: #111827;
}

.page-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 24px;
  border: 1px solid #dde5f1;
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(37, 99, 235, 0.08), rgba(20, 184, 166, 0.08)),
    #ffffff;
}

.eyebrow {
  margin: 0 0 8px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.page-hero h1 {
  margin: 0;
  font-size: 30px;
  line-height: 1.2;
}

.subtitle {
  max-width: 680px;
  margin: 10px 0 0;
  color: #64748b;
  line-height: 1.7;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(92px, 1fr));
  gap: 10px;
  min-width: 340px;
}

.metric-card {
  padding: 16px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.82);
}

.metric-card span {
  display: block;
  color: #64748b;
  font-size: 13px;
}

.metric-card strong {
  display: block;
  margin-top: 8px;
  font-size: 28px;
}

.toolbar,
.records-panel {
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.filter-input {
  width: 160px;
}

.filter-select {
  width: 170px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.section-header h2 {
  margin: 0;
  font-size: 20px;
}

.section-header p {
  margin: 6px 0 0;
  color: #64748b;
  line-height: 1.6;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-header h2 {
  margin: 0;
  font-size: 22px;
}

.risk-bar {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #e5e7eb;
}

.risk-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #22c55e, #f59e0b, #ef4444);
}

.detail-list {
  display: grid;
  gap: 12px;
  margin: 0;
}

.detail-list div {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.detail-list dt {
  color: #64748b;
  font-size: 12px;
}

.detail-list dd {
  margin: 6px 0 0;
  color: #111827;
  line-height: 1.6;
  word-break: break-word;
}

.path-text {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.json-block h3 {
  margin: 0 0 8px;
  font-size: 15px;
}

.json-block pre {
  max-height: 260px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  background: #0f172a;
  color: #dbeafe;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 900px) {
  .page-hero {
    flex-direction: column;
  }

  .hero-metrics {
    min-width: 0;
  }
}
</style>
