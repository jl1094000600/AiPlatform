<template>
  <div class="quality-page">
    <div class="quality-header">
      <div>
        <h2>{{ t('quality.title') }}</h2>
        <span class="muted">{{ t('quality.subtitle') }}</span>
      </div>
      <el-button type="primary" @click="loadAll">{{ t('common.refresh') }}</el-button>
    </div>

    <div class="metric-row">
      <div class="metric-card" v-for="item in overview" :key="item.label">
        <span>{{ item.label }}</span>
        <strong class="mono">{{ item.value }}</strong>
      </div>
    </div>

    <section class="quality-panel">
      <el-table :data="summary" v-loading="loading" stripe>
        <el-table-column prop="agentName" label="Agent" min-width="170">
          <template #default="{ row }">
            <div class="agent-cell">
              <strong>{{ row.agentName }}</strong>
              <span class="mono">{{ row.agentCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="accuracy" :label="t('quality.accuracy')" width="110" align="center">
          <template #default="{ row }">{{ metric(row.accuracy) }}</template>
        </el-table-column>
        <el-table-column prop="precisionScore" :label="t('quality.precision')" width="110" align="center">
          <template #default="{ row }">{{ metric(row.precisionScore) }}</template>
        </el-table-column>
        <el-table-column prop="recallScore" :label="t('quality.recall')" width="110" align="center">
          <template #default="{ row }">{{ metric(row.recallScore) }}</template>
        </el-table-column>
        <el-table-column prop="f1Score" label="F1" width="100" align="center">
          <template #default="{ row }">{{ metric(row.f1Score) }}</template>
        </el-table-column>
        <el-table-column prop="sampleCount" :label="t('quality.samples')" width="90" align="center" />
        <el-table-column prop="temperature" :label="t('quality.runtime')" min-width="160">
          <template #default="{ row }">
            <span class="mono">topK {{ row.topK || '-' }} / temp {{ row.temperature ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastRunTime" :label="t('quality.lastRun')" min-width="160">
          <template #default="{ row }">{{ formatTime(row.lastRunTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openConfig(row)">{{ t('quality.config') }}</el-button>
            <el-button size="small" type="primary" @click="runEvaluation(row)">{{ t('quality.evaluate') }}</el-button>
            <el-button size="small" @click="openResults(row)">{{ t('common.details') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="quality-panel trend-panel">
      <div class="panel-title">{{ t('quality.trend') }}</div>
      <el-empty v-if="trends.length === 0" :description="t('quality.noTrend')" />
      <div v-else class="trend-list">
        <div v-for="run in trends" :key="run.id" class="trend-row">
          <span class="mono">{{ formatDate(run.createTime) }}</span>
          <div class="trend-bar"><i :style="{ width: metricWidth(run.f1Score) }"></i></div>
          <span class="mono">F1 {{ metric(run.f1Score) }}</span>
        </div>
      </div>
    </section>

    <el-dialog v-model="configVisible" :title="t('quality.runtimeConfig')" width="560px">
      <el-form :model="configForm" label-position="top">
        <el-form-item :label="t('quality.model')">
          <el-select v-model="configForm.modelId" clearable filterable style="width: 100%">
            <el-option v-for="model in models" :key="model.id" :label="model.modelName" :value="model.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('quality.dataset')">
          <el-select v-model="configForm.datasetId" clearable filterable style="width: 100%">
            <el-option v-for="dataset in datasets" :key="dataset.id" :label="dataset.datasetName" :value="dataset.id" />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="topK">
            <el-input-number v-model="configForm.topK" :min="1" :max="100" />
          </el-form-item>
          <el-form-item label="temperature">
            <el-input-number v-model="configForm.temperature" :min="0" :max="2" :step="0.1" :precision="2" />
          </el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item :label="t('quality.inputField')">
            <el-input v-model="configForm.inputField" />
          </el-form-item>
          <el-form-item :label="t('quality.expectedField')">
            <el-input v-model="configForm.expectedField" />
          </el-form-item>
        </div>
        <el-form-item :label="t('quality.enabled')">
          <el-switch v-model="configEnabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveConfig">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultVisible" :title="t('quality.evalDetails')" width="900px">
      <el-table :data="results" stripe max-height="440">
        <el-table-column prop="sampleIndex" label="#" width="70" />
        <el-table-column prop="inputText" :label="t('quality.input')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="expectedOutput" :label="t('quality.expected')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="predictedOutput" :label="t('quality.predicted')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="matched" :label="t('quality.hit')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.matched === 1 ? 'success' : 'danger'">{{ row.matched === 1 ? t('quality.yes') : t('quality.no') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" :label="t('quality.time')" width="90" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useI18n } from '../i18n'

const { t } = useI18n()

const loading = ref(false)
const summary = ref([])
const trends = ref([])
const models = ref([])
const datasets = ref([])
const currentAgent = ref(null)
const configVisible = ref(false)
const resultVisible = ref(false)
const results = ref([])
const configEnabled = ref(true)
const configForm = reactive({
  modelId: null,
  datasetId: null,
  topK: 5,
  temperature: 0.7,
  inputField: 'input',
  expectedField: 'expectedOutput',
  enabled: 1
})

const overview = computed(() => {
  const evaluated = summary.value.filter(item => item.f1Score !== null && item.f1Score !== undefined)
  const avg = (key) => {
    if (evaluated.length === 0) return '-'
    const value = evaluated.reduce((sum, item) => sum + (item[key] || 0), 0) / evaluated.length
    return value.toFixed(2) + '%'
  }
  return [
    { label: t('quality.configured'), value: summary.value.filter(item => item.datasetId).length },
    { label: t('quality.evaluated'), value: evaluated.length },
    { label: t('quality.avgAccuracy'), value: avg('accuracy') },
    { label: t('quality.avgF1'), value: avg('f1Score') }
  ]
})

const loadAll = async () => {
  loading.value = true
  try {
    const [summaryRes, modelsRes, datasetsRes] = await Promise.all([
      api.getAgentQualitySummary(),
      api.getModels({ pageNum: 1, pageSize: 100 }),
      api.getDatasets({ pageNum: 1, pageSize: 100 })
    ])
    summary.value = summaryRes.data.data || []
    models.value = modelsRes.data.data?.records || []
    datasets.value = datasetsRes.data.data?.records || []
    if (summary.value[0]) {
      await loadTrends(summary.value[0].agentId)
    }
  } catch (error) {
    ElMessage.error(t('quality.loadFailed'))
  } finally {
    loading.value = false
  }
}

const loadTrends = async (agentId) => {
  const res = await api.getAgentQualityTrends({ agentId })
  trends.value = res.data.data || []
}

const openConfig = async (row) => {
  currentAgent.value = row
  const res = await api.getAgentRuntimeConfig(row.agentId)
  Object.assign(configForm, {
    modelId: res.data.data?.modelId || null,
    datasetId: res.data.data?.datasetId || null,
    topK: res.data.data?.topK || 5,
    temperature: res.data.data?.temperature ?? 0.7,
    inputField: res.data.data?.inputField || 'input',
    expectedField: res.data.data?.expectedField || 'expectedOutput',
    enabled: res.data.data?.enabled ?? 1
  })
  configEnabled.value = configForm.enabled === 1
  configVisible.value = true
}

const saveConfig = async () => {
  configForm.enabled = configEnabled.value ? 1 : 0
  await api.updateAgentRuntimeConfig(currentAgent.value.agentId, configForm)
  ElMessage.success(t('quality.saved'))
  configVisible.value = false
  await loadAll()
}

const runEvaluation = async (row) => {
  const res = await api.runAgentQualityEvaluation({ agentId: row.agentId })
  if (res.data.code === 200) {
    ElMessage.success(t('quality.finished'))
    await loadAll()
    await loadTrends(row.agentId)
  }
}

const openResults = async (row) => {
  const runs = await api.getAgentQualityEvaluations({ agentId: row.agentId, pageNum: 1, pageSize: 1 })
  const latest = runs.data.data?.records?.[0]
  if (!latest) {
    ElMessage.info(t('quality.noDetails'))
    return
  }
  const res = await api.getAgentQualityResults(latest.id)
  results.value = res.data.data || []
  resultVisible.value = true
}

const metric = (value) => value === null || value === undefined ? '-' : Number(value).toFixed(2) + '%'
const metricWidth = (value) => `${Math.max(4, Math.min(100, Number(value) || 0))}%`
const formatTime = (value) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const formatDate = (value) => value ? new Date(value).toLocaleDateString('zh-CN') : '-'

onMounted(loadAll)
</script>

<style scoped>
.quality-page { color: var(--text-primary); }
.quality-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.quality-header h2 { font-size: 24px; margin-bottom: 6px; }
.muted { color: var(--text-muted); }
.metric-row { display: grid; grid-template-columns: repeat(4, minmax(160px, 1fr)); gap: 14px; margin-bottom: 16px; }
.metric-card, .quality-panel { background: var(--glass-bg); border: 1px solid var(--glass-border); border-radius: 8px; padding: 16px; }
.metric-card { display: flex; flex-direction: column; gap: 8px; }
.metric-card span { color: var(--text-muted); }
.metric-card strong { font-size: 26px; color: var(--accent-cyan); }
.agent-cell { display: flex; flex-direction: column; gap: 3px; }
.agent-cell span { color: var(--text-muted); font-size: 12px; }
.trend-panel { margin-top: 16px; }
.panel-title { font-weight: 700; margin-bottom: 12px; }
.trend-list { display: flex; flex-direction: column; gap: 10px; }
.trend-row { display: grid; grid-template-columns: 120px 1fr 100px; gap: 12px; align-items: center; color: var(--text-secondary); }
.trend-bar { height: 10px; background: #eef2f7; border-radius: 8px; overflow: hidden; }
.trend-bar i { display: block; height: 100%; background: #2563eb; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
@media (max-width: 1100px) {
  .metric-row { grid-template-columns: repeat(2, 1fr); }
}
</style>
