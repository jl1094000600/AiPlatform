<template>
  <div class="badcase-page">
    <section class="metrics-grid">
      <div class="metric-card">
        <span>Total Badcases</span>
        <strong>{{ stats.total || 0 }}</strong>
      </div>
      <div class="metric-card">
        <span>PRD Stage</span>
        <strong>{{ stats.byStage?.PRD || 0 }}</strong>
      </div>
      <div class="metric-card">
        <span>Code Stage</span>
        <strong>{{ stats.byStage?.CODE || 0 }}</strong>
      </div>
      <div class="metric-card">
        <span>Manual Source</span>
        <strong>{{ stats.bySource?.MANUAL || 0 }}</strong>
      </div>
    </section>

    <section class="toolbar">
      <el-input v-model="filters.keyword" clearable placeholder="Search title, reason, project, code" />
      <el-select v-model="filters.stage" clearable placeholder="Stage">
        <el-option label="PRD" value="PRD" />
        <el-option label="CODE" value="CODE" />
        <el-option label="PRD_TO_CODE" value="PRD_TO_CODE" />
      </el-select>
      <el-select v-model="filters.sourceType" clearable placeholder="Source">
        <el-option label="SEED" value="SEED" />
        <el-option label="MANUAL" value="MANUAL" />
      </el-select>
      <el-select v-model="filters.severity" clearable placeholder="Severity">
        <el-option label="P0" value="P0" />
        <el-option label="P1" value="P1" />
        <el-option label="P2" value="P2" />
        <el-option label="P3" value="P3" />
      </el-select>
      <el-button type="primary" @click="loadBadCases">Query</el-button>
    </section>

    <el-table v-loading="loading" :data="records" class="badcase-table" @row-click="openDetail">
      <el-table-column prop="caseCode" label="Case" width="150" />
      <el-table-column prop="stage" label="Stage" width="120">
        <template #default="{ row }">
          <el-tag :type="stageTagType(row.stage)">{{ row.stage }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="badcaseType" label="Type" min-width="210" show-overflow-tooltip />
      <el-table-column prop="severity" label="Severity" width="100">
        <template #default="{ row }">
          <el-tag :type="severityTagType(row.severity)">{{ row.severity }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sourceType" label="Source" width="110" />
      <el-table-column prop="requirementTitle" label="Requirement" min-width="240" show-overflow-tooltip />
      <el-table-column prop="failureReason" label="Failure Reason" min-width="300" show-overflow-tooltip />
      <el-table-column prop="reviewedBy" label="Reviewer" width="120" />
      <el-table-column prop="createTime" label="Created" width="180" />
    </el-table>

    <div class="pagination-row">
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        @size-change="loadBadCases"
        @current-change="loadBadCases"
      />
    </div>

    <el-drawer v-model="detailVisible" title="Badcase Detail" size="50%">
      <div v-if="selected" class="detail-stack">
        <div class="detail-head">
          <strong>{{ selected.caseCode }}</strong>
          <el-tag>{{ selected.sourceType }}</el-tag>
          <el-tag :type="stageTagType(selected.stage)">{{ selected.stage }}</el-tag>
          <el-tag :type="severityTagType(selected.severity)">{{ selected.severity }}</el-tag>
        </div>
        <div class="detail-block">
          <label>Requirement</label>
          <p>{{ selected.requirementTitle || '-' }}</p>
        </div>
        <div class="detail-block">
          <label>Input Prompt</label>
          <pre>{{ selected.inputPrompt || '-' }}</pre>
        </div>
        <div v-if="selected.generatedPrd" class="detail-block">
          <label>Generated PRD</label>
          <pre>{{ selected.generatedPrd }}</pre>
        </div>
        <div v-if="selected.generatedCode" class="detail-block">
          <label>Generated Code</label>
          <pre>{{ selected.generatedCode }}</pre>
        </div>
        <div class="detail-block">
          <label>Expected Behavior</label>
          <pre>{{ selected.expectedBehavior || '-' }}</pre>
        </div>
        <div class="detail-block danger">
          <label>Failure Reason</label>
          <pre>{{ selected.failureReason || '-' }}</pre>
        </div>
        <div class="detail-meta">
          <span>Pipeline: {{ selected.pipelineId || '-' }}</span>
          <span>Stage Run: {{ selected.stageRunId || '-' }}</span>
          <span>Batch: {{ selected.batchId || '-' }}</span>
          <span>Tags: {{ selected.tags || '-' }}</span>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import api from '../api'

const loading = ref(false)
const records = ref([])
const stats = ref({})
const detailVisible = ref(false)
const selected = ref(null)

const filters = reactive({
  keyword: '',
  stage: '',
  sourceType: '',
  severity: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

const loadStats = async () => {
  const res = await api.getBadCaseStatistics()
  stats.value = res.data.data || {}
}

const loadBadCases = async () => {
  loading.value = true
  try {
    const res = await api.getBadCases({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      keyword: filters.keyword || undefined,
      stage: filters.stage || undefined,
      sourceType: filters.sourceType || undefined,
      severity: filters.severity || undefined
    })
    const page = res.data.data || {}
    records.value = page.records || []
    pagination.total = Number(page.total || 0)
  } finally {
    loading.value = false
  }
}

const openDetail = (row) => {
  selected.value = row
  detailVisible.value = true
}

const stageTagType = (stage) => {
  if (stage === 'PRD') return 'success'
  if (stage === 'CODE') return 'warning'
  return 'info'
}

const severityTagType = (severity) => {
  if (severity === 'P0') return 'danger'
  if (severity === 'P1') return 'warning'
  if (severity === 'P2') return 'info'
  return ''
}

onMounted(async () => {
  await Promise.all([loadStats(), loadBadCases()])
})
</script>

<style scoped>
.badcase-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  min-height: 86px;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.metric-card span {
  font-size: 13px;
  color: var(--text-muted);
}

.metric-card strong {
  font-size: 28px;
  color: var(--text-primary);
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 150px 150px 150px 96px;
  gap: 10px;
  align-items: center;
}

.badcase-table {
  width: 100%;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
}

.detail-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.detail-head strong {
  font-size: 18px;
  margin-right: 4px;
}

.detail-block {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: #ffffff;
  padding: 12px;
}

.detail-block label {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
}

.detail-block p {
  margin: 0;
  color: var(--text-primary);
}

.detail-block pre {
  margin: 0;
  max-height: 260px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.6;
  color: #111827;
}

.detail-block.danger {
  border-color: #fecaca;
  background: #fff7f7;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--text-muted);
  font-size: 12px;
}

@media (max-width: 960px) {
  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
