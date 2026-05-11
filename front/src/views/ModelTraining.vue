<template>
  <div class="training-page">
    <div class="page-head">
      <div>
        <h2>{{ t('training.title') }}</h2>
        <p>{{ t('training.subtitle') }}</p>
      </div>
      <el-button @click="refreshAll">
        <Refresh class="btn-icon" /> {{ t('training.refresh') }}
      </el-button>
    </div>

    <el-tabs v-model="activeTab" class="training-tabs">
      <el-tab-pane label="训练任务" name="jobs">
        <div class="training-layout">
          <section class="panel config-panel">
            <div class="panel-title">{{ t('training.config') }}</div>
            <el-form :model="form" label-position="top">
              <el-form-item :label="t('training.modelPath')">
                <el-input v-model="form.modelPath" />
              </el-form-item>
              <el-form-item :label="t('training.trainData')">
                <el-select
                  v-model="form.trainData"
                  filterable
                  placeholder="请选择训练数据"
                  style="width: 100%"
                  :loading="datasetsLoading"
                >
                  <el-option
                    v-for="dataset in datasets"
                    :key="dataset.path"
                    :label="datasetLabel(dataset)"
                    :value="dataset.path"
                  >
                    <div class="dataset-option">
                      <span>{{ dataset.name }}</span>
                      <small>{{ dataset.records }} records · {{ dataset.source }}</small>
                    </div>
                  </el-option>
                </el-select>
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

              <div class="action-row">
                <el-button type="primary" :loading="starting" @click="startTraining">
                  <VideoPlay class="btn-icon" /> {{ t('training.start') }}
                </el-button>
                <el-button @click="activeTab = 'datasets'">
                  <FolderOpened class="btn-icon" /> 配置训练数据
                </el-button>
              </div>
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
      </el-tab-pane>

      <el-tab-pane label="训练数据" name="datasets">
        <div class="dataset-layout">
          <section class="panel dataset-table-panel">
            <div class="panel-head">
              <div class="panel-title">训练数据集</div>
              <el-button @click="loadDatasets">
                <Refresh class="btn-icon" /> 刷新
              </el-button>
            </div>
            <el-table :data="datasets" v-loading="datasetsLoading" stripe>
              <el-table-column prop="name" label="文件名" min-width="180" />
              <el-table-column prop="source" label="来源" width="110">
                <template #default="{ row }">
                  <el-tag>{{ row.source }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="records" label="样本数" width="100" />
              <el-table-column label="大小" width="110">
                <template #default="{ row }">{{ formatBytes(row.sizeBytes) }}</template>
              </el-table-column>
              <el-table-column prop="path" label="路径" min-width="260" show-overflow-tooltip />
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" @click="chooseDataset(row)">使用</el-button>
                </template>
              </el-table-column>
              <template #empty>
                <span>暂无训练数据</span>
              </template>
            </el-table>
          </section>

          <section class="panel dataset-tools-panel">
            <div class="panel-title">导入 JSONL</div>
            <div class="tool-row">
              <input ref="fileInput" type="file" accept=".jsonl" class="file-input" @change="handleDatasetFile" />
              <el-button :loading="importing" @click="openDatasetFile">
                <Upload class="btn-icon" /> 选择文件
              </el-button>
              <span class="file-name">{{ importForm.fileName || '未选择文件' }}</span>
            </div>
            <el-input
              v-model="importForm.content"
              type="textarea"
              :rows="8"
              placeholder='{"query":"...","pos":["..."],"neg":["..."]}'
            />
            <el-button class="tool-submit" type="primary" :loading="importing" @click="importDataset">
              导入数据
            </el-button>

            <div class="tool-divider"></div>

            <div class="panel-title">大模型生成</div>
            <el-form :model="mockForm" label-position="top">
              <el-form-item label="生成模型">
                <el-select v-model="mockForm.modelCode" filterable clearable placeholder="默认使用最新启用模型" style="width: 100%">
                  <el-option
                    v-for="model in generationModels"
                    :key="model.modelCode"
                    :label="`${model.modelName || model.modelCode} (${model.modelCode})`"
                    :value="model.modelCode"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="文件名">
                <el-input v-model="mockForm.fileName" placeholder="mock-bgem3-training.jsonl" />
              </el-form-item>
              <el-form-item label="主题">
                <el-input v-model="mockForm.topic" placeholder="AI Platform" />
              </el-form-item>
              <el-form-item label="样本数">
                <el-input-number v-model="mockForm.count" :min="1" :max="500" style="width: 100%" />
              </el-form-item>
              <el-button type="primary" :loading="previewing" @click="previewMockDataset">
                <MagicStick class="btn-icon" /> 调用模型生成预览
              </el-button>
            </el-form>

            <div v-if="mockPreview.content" class="preview-block">
              <div class="panel-head preview-head">
                <div>
                  <div class="panel-title">预览与保存</div>
                  <div class="preview-meta">由模型 {{ mockPreview.modelCode || '-' }} 生成</div>
                </div>
                <el-button type="primary" :loading="savingDataset" @click="saveMockDataset">
                  保存数据
                </el-button>
              </div>
              <el-table :data="mockPreview.records" size="small" max-height="220" border>
                <el-table-column prop="query" label="query" min-width="140" show-overflow-tooltip />
                <el-table-column label="pos" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.pos?.join(' | ') }}</template>
                </el-table-column>
                <el-table-column label="neg" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.neg?.join(' | ') }}</template>
                </el-table-column>
              </el-table>
              <el-input
                v-model="mockPreview.content"
                class="preview-editor"
                type="textarea"
                :rows="8"
              />
            </div>
          </section>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { FolderOpened, MagicStick, Refresh, Upload, VideoPlay } from '@element-plus/icons-vue'
import api from '../api'
import { useI18n } from '../i18n'

const { t } = useI18n()

const activeTab = ref('jobs')
const loading = ref(false)
const starting = ref(false)
const datasetsLoading = ref(false)
const importing = ref(false)
const previewing = ref(false)
const savingDataset = ref(false)
const jobs = ref([])
const datasets = ref([])
const generationModels = ref([])
const selectedJob = ref(null)
const logs = ref('')
const fileInput = ref(null)
let pollTimer = null

const form = reactive({
  modelPath: 'BAAI/bge-m3',
  trainData: '',
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

const importForm = reactive({
  fileName: '',
  content: ''
})

const mockForm = reactive({
  modelCode: '',
  fileName: 'mock-bgem3-training.jsonl',
  topic: 'AI Platform',
  count: 12
})

const mockPreview = reactive({
  fileName: '',
  topic: '',
  count: 0,
  modelCode: '',
  records: [],
  content: ''
})

const refreshAll = async () => {
  await Promise.all([loadJobs(), loadDatasets(), loadGenerationModels()])
}

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
    ElMessage.error(error.response?.data?.message || t('training.startFailed'))
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

const loadDatasets = async () => {
  datasetsLoading.value = true
  try {
    const res = await api.getModelTrainingDatasets()
    datasets.value = res.data.data || []
    if (!form.trainData && datasets.value.length) {
      const preferred = datasets.value.find(dataset => dataset.path.endsWith('/train.jsonl')) || datasets.value[0]
      form.trainData = preferred.path
    }
  } catch (error) {
    ElMessage.error('训练数据加载失败')
  } finally {
    datasetsLoading.value = false
  }
}

const loadGenerationModels = async () => {
  try {
    const res = await api.getModels({ pageNum: 1, pageSize: 100 })
    const records = res.data.data?.records || []
    generationModels.value = records.filter(model => model.status === 1)
    if (!mockForm.modelCode && generationModels.value.length) {
      mockForm.modelCode = generationModels.value[0].modelCode
    }
  } catch (error) {
    generationModels.value = []
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

const openDatasetFile = () => {
  fileInput.value?.click()
}

const handleDatasetFile = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  importForm.fileName = file.name
  importForm.content = await file.text()
  event.target.value = ''
}

const importDataset = async () => {
  if (!importForm.fileName || !importForm.content.trim()) {
    ElMessage.warning('请选择或粘贴 JSONL 数据')
    return
  }
  importing.value = true
  try {
    const res = await api.importModelTrainingDataset({ ...importForm })
    await loadDatasets()
    chooseDataset(res.data.data)
    importForm.fileName = ''
    importForm.content = ''
    ElMessage.success('训练数据已导入')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '训练数据导入失败')
  } finally {
    importing.value = false
  }
}

const previewMockDataset = async () => {
  previewing.value = true
  try {
    const res = await api.previewModelTrainingMockDataset({ ...mockForm })
    const data = res.data.data || {}
    mockPreview.fileName = data.fileName || mockForm.fileName
    mockPreview.topic = data.topic || mockForm.topic
    mockPreview.count = data.count || mockForm.count
    mockPreview.modelCode = data.modelCode || mockForm.modelCode
    mockPreview.records = data.records || []
    mockPreview.content = data.content || ''
    ElMessage.success('大模型数据已生成预览')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '大模型训练数据预览失败')
  } finally {
    previewing.value = false
  }
}

const saveMockDataset = async () => {
  if (!mockPreview.content.trim()) {
    ElMessage.warning('请先生成或填写预览数据')
    return
  }
  savingDataset.value = true
  try {
    const res = await api.saveModelTrainingDataset({
      fileName: mockPreview.fileName || mockForm.fileName,
      datasetName: mockPreview.fileName || mockForm.fileName,
      description: `Mock data for ${mockPreview.topic || mockForm.topic}`,
      source: 'mock',
      content: mockPreview.content
    })
    await loadDatasets()
    chooseDataset(res.data.data)
    ElMessage.success('训练数据已保存')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '训练数据保存失败')
  } finally {
    savingDataset.value = false
  }
}

const chooseDataset = (dataset) => {
  if (!dataset?.path) return
  form.trainData = dataset.path
  activeTab.value = 'jobs'
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

const datasetLabel = (dataset) => `${dataset.name} (${dataset.records} records)`

const formatBytes = (bytes) => {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

onMounted(async () => {
  await refreshAll()
  if (jobs.value.some(job => job.status === 'PENDING' || job.status === 'RUNNING')) {
    startPolling()
  }
})

onUnmounted(stopPolling)
</script>

<style scoped>
.training-page { color: var(--text-primary); }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.page-head h2 { font-size: 28px; margin-bottom: 6px; }
.page-head p { color: var(--text-muted); }
.training-tabs { min-width: 0; }
.btn-icon { width: 16px; height: 16px; margin-right: 6px; }
.training-layout { display: grid; grid-template-columns: minmax(360px, 0.85fr) minmax(520px, 1.15fr); gap: 16px; }
.bottom-layout { margin-top: 16px; grid-template-columns: minmax(520px, 1.2fr) minmax(360px, 0.8fr); }
.dataset-layout { display: grid; grid-template-columns: minmax(620px, 1.3fr) minmax(360px, 0.7fr); gap: 16px; }
.panel { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: 8px; padding: 16px; }
.panel-title { font-weight: 700; margin-bottom: 14px; }
.panel-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 14px; }
.panel-head .panel-title { margin-bottom: 0; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.switch-row { display: flex; gap: 18px; margin-bottom: 16px; }
.action-row { display: flex; flex-wrap: wrap; gap: 10px; }
.dataset-option { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-width: 0; }
.dataset-option span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dataset-option small { color: var(--text-muted); flex-shrink: 0; }
.tool-row { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; min-width: 0; }
.file-input { display: none; }
.file-name { color: var(--text-muted); font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tool-submit { margin-top: 12px; }
.tool-divider { height: 1px; background: var(--border-color); margin: 18px 0; }
.preview-block { margin-top: 16px; }
.preview-head { margin-bottom: 10px; }
.preview-head .panel-title { margin-bottom: 4px; }
.preview-meta { color: var(--text-muted); font-size: 12px; }
.preview-editor { margin-top: 10px; }
.logs-panel pre { min-height: 280px; max-height: 420px; overflow: auto; padding: 12px; border-radius: 8px; background: #0f172a; color: #e5e7eb; white-space: pre-wrap; font-family: var(--font-mono); font-size: 12px; line-height: 1.6; }
.metric-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.metric-item { border: 1px solid var(--border-color); border-radius: 8px; padding: 12px; min-width: 0; }
.metric-item span { display: block; color: var(--text-muted); font-size: 12px; margin-bottom: 6px; word-break: break-word; }
.metric-item strong { font-size: 18px; word-break: break-word; }
.empty-metrics { color: var(--text-muted); padding: 24px 0; }
@media (max-width: 1100px) {
  .training-layout, .bottom-layout, .dataset-layout { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .page-head { flex-direction: column; gap: 12px; }
  .form-grid, .metric-grid { grid-template-columns: 1fr; }
}
</style>
