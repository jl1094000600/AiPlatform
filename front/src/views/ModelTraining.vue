<template>
  <div class="training-page">
    <div class="page-head">
      <div>
        <h2>{{ t('training.title') }}</h2>
        <p>{{ t('training.subtitle') }}</p>
      </div>
      <el-button @click="loadJobs">
        <Refresh class="btn-icon" /> {{ t('training.refresh') }}
      </el-button>
    </div>

    <div class="training-layout">
      <section class="panel config-panel">
        <div class="panel-title">{{ t('training.config') }}</div>
        <el-form :model="form" label-position="top">
          <el-form-item :label="t('training.modelPath')">
            <el-input v-model="form.modelPath" />
          </el-form-item>
          <el-form-item :label="t('training.trainData')">
            <el-input v-model="form.trainData" />
          </el-form-item>
          <el-form-item :label="t('training.outputDir')">
            <el-input v-model="form.outputDir" />
          </el-form-item>

          <div class="form-grid">
            <el-form-item :label="t('training.epochs')">
              <el-input-number v-model="form.epochs" :min="1" :max="50" style="width: 100%" />
            </el-form-item>
            <el-form-item :label="t('training.learningRate')">
              <el-input v-model="form.learningRate" />
            </el-form-item>
          </div>

          <div class="form-grid">
            <el-form-item :label="t('training.queryMaxLen')">
              <el-input-number v-model="form.queryMaxLen" :min="32" :max="8192" style="width: 100%" />
            </el-form-item>
            <el-form-item :label="t('training.passageMaxLen')">
              <el-input-number v-model="form.passageMaxLen" :min="64" :max="8192" style="width: 100%" />
            </el-form-item>
          </div>

          <div class="form-grid">
            <el-form-item :label="t('training.trainGroupSize')">
              <el-input-number v-model="form.trainGroupSize" :min="2" :max="64" style="width: 100%" />
            </el-form-item>
            <el-form-item :label="t('training.device')">
              <el-input v-model="form.device" placeholder="cuda:0 / cpu" clearable />
            </el-form-item>
          </div>

          <div class="switch-row">
            <el-checkbox v-model="form.dryRun">{{ t('training.dryRun') }}</el-checkbox>
            <el-checkbox v-model="form.unifiedFinetuning">{{ t('training.unifiedFinetuning') }}</el-checkbox>
          </div>

          <el-button type="primary" :loading="starting" @click="startTraining">
            <VideoPlay class="btn-icon" /> {{ t('training.start') }}
          </el-button>
        </el-form>
      </section>

      <section class="panel jobs-panel">
        <div class="panel-title">{{ t('training.jobs') }}</div>
        <el-table :data="jobs" v-loading="loading" stripe @row-click="selectJob">
          <el-table-column prop="id" label="Job ID" min-width="150" />
          <el-table-column prop="status" :label="t('common.status')" width="120">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="outputDir" :label="t('training.outputDir')" min-width="220" />
          <el-table-column prop="createTime" label="Created" width="180" />
          <template #empty>
            <span>{{ t('training.emptyJobs') }}</span>
          </template>
        </el-table>
      </section>
    </div>

    <div class="training-layout bottom-layout">
      <section class="panel logs-panel">
        <div class="panel-title">{{ t('training.logs') }}</div>
        <pre>{{ logs || t('training.noLogs') }}</pre>
      </section>

      <section class="panel metrics-panel">
        <div class="panel-title">{{ t('training.metrics') }}</div>
        <div v-if="selectedJob?.metrics" class="metric-grid">
          <div v-for="(value, key) in selectedJob.metrics" :key="key" class="metric-item">
            <span>{{ key }}</span>
            <strong>{{ value }}</strong>
          </div>
        </div>
        <div v-else class="empty-metrics">{{ t('training.noMetrics') }}</div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, VideoPlay } from '@element-plus/icons-vue'
import api from '../api'
import { useI18n } from '../i18n'

const { t } = useI18n()

const loading = ref(false)
const starting = ref(false)
const jobs = ref([])
const selectedJob = ref(null)
const logs = ref('')
let pollTimer = null

const form = reactive({
  modelPath: 'BAAI/bge-m3',
  trainData: 'bge-m3-training/data/train.jsonl',
  outputDir: 'bge-m3-training/output/bge-m3-ft',
  epochs: 1,
  learningRate: '1e-5',
  queryMaxLen: 256,
  passageMaxLen: 512,
  trainGroupSize: 4,
  unifiedFinetuning: false,
  dryRun: true,
  device: ''
})

const startTraining = async () => {
  if (!form.modelPath || !form.trainData || !form.outputDir) {
    ElMessage.warning(t('training.required'))
    return
  }
  starting.value = true
  try {
    const res = await api.createModelTrainingJob({
      ...form,
      learningRate: Number(form.learningRate)
    })
    selectedJob.value = res.data.data
    ElMessage.success(t('training.created'))
    await loadJobs()
    startPolling()
  } catch (error) {
    ElMessage.error(t('training.startFailed'))
  } finally {
    starting.value = false
  }
}

const loadJobs = async () => {
  loading.value = true
  try {
    const res = await api.getModelTrainingJobs()
    jobs.value = res.data.data || []
    if (!selectedJob.value && jobs.value.length) {
      await selectJob(jobs.value[0])
    } else if (selectedJob.value) {
      const current = jobs.value.find(job => job.id === selectedJob.value.id)
      if (current) {
        await selectJob(current)
      }
    }
  } catch (error) {
    ElMessage.error(t('training.loadFailed'))
  } finally {
    loading.value = false
  }
}

const selectJob = async (job) => {
  if (!job?.id) return
  const [jobRes, logRes] = await Promise.all([
    api.getModelTrainingJob(job.id),
    api.getModelTrainingLogs(job.id)
  ])
  selectedJob.value = jobRes.data.data
  logs.value = logRes.data.data || ''
}

const startPolling = () => {
  stopPolling()
  pollTimer = window.setInterval(async () => {
    await loadJobs()
    if (!jobs.value.some(job => job.status === 'PENDING' || job.status === 'RUNNING')) {
      stopPolling()
    }
  }, 3000)
}

const stopPolling = () => {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

const statusType = (status) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

onMounted(async () => {
  await loadJobs()
  if (jobs.value.some(job => job.status === 'PENDING' || job.status === 'RUNNING')) {
    startPolling()
  }
})

onUnmounted(stopPolling)
</script>

<style scoped>
.training-page { color: var(--text-primary); }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; }
.page-head h2 { font-size: 28px; margin-bottom: 6px; }
.page-head p { color: var(--text-muted); }
.btn-icon { width: 16px; height: 16px; margin-right: 6px; }
.training-layout { display: grid; grid-template-columns: minmax(360px, 0.85fr) minmax(520px, 1.15fr); gap: 16px; }
.bottom-layout { margin-top: 16px; grid-template-columns: minmax(520px, 1.2fr) minmax(360px, 0.8fr); }
.panel { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: 8px; padding: 16px; }
.panel-title { font-weight: 700; margin-bottom: 14px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.switch-row { display: flex; gap: 18px; margin-bottom: 16px; }
.logs-panel pre { min-height: 280px; max-height: 420px; overflow: auto; padding: 12px; border-radius: 8px; background: #0f172a; color: #e5e7eb; white-space: pre-wrap; font-family: var(--font-mono); font-size: 12px; line-height: 1.6; }
.metric-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.metric-item { border: 1px solid var(--border-color); border-radius: 8px; padding: 12px; min-width: 0; }
.metric-item span { display: block; color: var(--text-muted); font-size: 12px; margin-bottom: 6px; word-break: break-word; }
.metric-item strong { font-size: 18px; word-break: break-word; }
.empty-metrics { color: var(--text-muted); padding: 24px 0; }
@media (max-width: 1100px) {
  .training-layout, .bottom-layout { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .page-head { flex-direction: column; gap: 12px; }
  .form-grid, .metric-grid { grid-template-columns: 1fr; }
}
</style>
