<template>
  <div class="rag-page">
    <div class="page-header">
      <div>
        <h2>{{ t('rag.title') }}</h2>
        <p>{{ t('rag.subtitle') }}</p>
      </div>
      <el-button :loading="loading" @click="loadPageData">{{ t('common.refresh') }}</el-button>
    </div>

    <div class="rag-grid">
      <section class="ingest-panel">
        <div class="section-head">
          <h3>{{ t('rag.ingest') }}</h3>
          <span>{{ t('rag.modelHint') }}</span>
        </div>

        <el-form label-position="top" class="rag-form">
          <div class="form-grid">
            <el-form-item :label="t('rag.collection')" required>
              <el-input v-model="form.collectionName" placeholder="default_knowledge" />
            </el-form-item>
            <el-form-item :label="t('rag.documentTitle')">
              <el-input v-model="form.documentTitle" placeholder="Product requirements" />
            </el-form-item>
          </div>

          <el-form-item :label="t('rag.embeddingModel')" required>
            <el-select v-model="form.embeddingModelId" filterable style="width: 100%">
              <el-option
                v-for="model in embeddingModels"
                :key="model.id"
                :label="`${model.modelName} / ${model.modelCode}`"
                :value="model.id"
              />
            </el-select>
          </el-form-item>

          <div class="form-grid">
            <el-form-item :label="t('rag.chunkMode')">
              <el-radio-group v-model="form.chunkMode" class="mode-control">
                <el-radio-button
                  v-for="option in chunkModeOptions"
                  :key="option.value"
                  :label="option.value"
                >
                  {{ option.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item :label="t('rag.contentType')">
              <el-select v-model="form.contentType" style="width: 100%">
                <el-option :label="t('rag.contentAuto')" value="AUTO" />
                <el-option :label="t('rag.contentDocument')" value="DOCUMENT" />
                <el-option :label="t('rag.contentCode')" value="CODE" />
              </el-select>
            </el-form-item>
          </div>

          <el-form-item v-if="form.chunkMode === 'HYBRID'" :label="t('rag.semanticModel')">
            <el-select
              v-model="form.semanticModelId"
              clearable
              filterable
              style="width: 100%"
              :placeholder="t('rag.semanticModelPlaceholder')"
            >
              <el-option
                v-for="model in semanticModels"
                :key="model.id"
                :label="`${model.modelName} / ${model.modelCode}`"
                :value="model.id"
              />
            </el-select>
          </el-form-item>

          <div class="form-grid">
            <el-form-item :label="t('rag.chromaUrl')">
              <el-input v-model="form.chromaUrl" placeholder="http://localhost:9000" />
            </el-form-item>
            <div class="number-grid">
              <el-form-item :label="t('rag.chunkSize')">
                <el-input-number v-model="form.chunkSize" :min="100" :max="4000" :step="100" />
              </el-form-item>
              <el-form-item :label="t('rag.chunkOverlap')">
                <el-input-number v-model="form.chunkOverlap" :min="0" :max="1000" :step="20" />
              </el-form-item>
            </div>
          </div>

          <el-form-item :label="t('rag.content')" required>
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="14"
              maxlength="200000"
              show-word-limit
              placeholder="Paste the document text to be chunked and embedded."
            />
          </el-form-item>

          <div class="form-actions">
            <el-button type="primary" :loading="submitting" @click="handleIngest">
              {{ t('rag.ingest') }}
            </el-button>
          </div>
        </el-form>
      </section>

      <section class="history-panel">
        <div class="section-head">
          <h3>{{ t('rag.history') }}</h3>
          <span>Chroma: {{ form.chromaUrl }}</span>
        </div>

        <el-table v-if="records.length" :data="records" border>
          <el-table-column prop="collectionName" :label="t('rag.collection')" min-width="150" />
          <el-table-column prop="documentTitle" :label="t('rag.documentTitle')" min-width="180" />
          <el-table-column prop="embeddingModelCode" :label="t('rag.model')" min-width="150" />
          <el-table-column prop="chunkMode" :label="t('rag.chunkMode')" width="110" />
          <el-table-column prop="contentType" :label="t('rag.contentType')" width="110" />
          <el-table-column prop="chunkCount" :label="t('rag.chunks')" width="90" />
          <el-table-column :label="t('common.status')" width="110">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" :label="t('rag.createdAt')" min-width="170" />
        </el-table>

        <div v-else class="empty-state">
          <p>{{ t('rag.emptyHistory') }}</p>
        </div>
      </section>
    </div>

    <section class="chroma-panel">
      <div class="section-head">
        <h3>当前 Chroma 内容</h3>
        <span>直接读取当前向量库集合与文档块</span>
      </div>

      <el-table v-if="collections.length" :data="collections" border v-loading="collectionsLoading">
        <el-table-column prop="name" label="集合名称" min-width="180" />
        <el-table-column prop="id" label="Collection ID" min-width="260" show-overflow-tooltip />
        <el-table-column prop="dimension" label="维度" width="90" />
        <el-table-column prop="count" label="文档块" width="100" />
        <el-table-column label="元数据" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ formatMetadata(row.metadata) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button size="small" @click="openDocuments(row)">查看文档块</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-else class="empty-state">
        <p>暂无 Chroma 集合，或当前地址无法读取。</p>
      </div>
    </section>

    <el-drawer v-model="documentsVisible" :title="documentsTitle" size="720px">
      <div class="documents-toolbar">
        <span>共 {{ documentsTotal }} 个文档块</span>
        <el-button size="small" :loading="documentsLoading" @click="loadDocuments">刷新</el-button>
      </div>
      <div v-loading="documentsLoading" class="document-list">
        <div v-for="doc in documents" :key="doc.id" class="document-item">
          <div class="document-head">
            <strong>{{ doc.id }}</strong>
            <el-tag size="small">chunk {{ doc.chunkIndex ?? '-' }}</el-tag>
          </div>
          <div class="document-meta">
            <span>{{ doc.documentTitle || '-' }}</span>
            <span>recordId: {{ doc.recordId ?? '-' }}</span>
          </div>
          <pre>{{ doc.preview || doc.document }}</pre>
        </div>
        <div v-if="!documents.length" class="empty-state">
          <p>暂无文档块</p>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useI18n } from '../i18n'

const { t } = useI18n()

const loading = ref(false)
const submitting = ref(false)
const models = ref([])
const records = ref([])
const collections = ref([])
const collectionsLoading = ref(false)
const documentsVisible = ref(false)
const documentsLoading = ref(false)
const documents = ref([])
const documentsTotal = ref(0)
const currentCollection = ref(null)
const chunkModeOptions = computed(() => [
  { label: t('rag.chunkModeFixed'), value: 'FIXED' },
  { label: t('rag.chunkModeHybrid'), value: 'HYBRID' }
])

const form = reactive({
  collectionName: 'default_knowledge',
  documentTitle: '',
  embeddingModelId: null,
  chunkMode: 'FIXED',
  contentType: 'AUTO',
  semanticModelId: null,
  chromaUrl: 'http://localhost:9000',
  chunkSize: 800,
  chunkOverlap: 100,
  content: ''
})

const embeddingModels = computed(() => {
  const enabled = models.value.filter(model => model.status === 1)
  const preferred = enabled.filter(model => {
    const text = `${model.modelName || ''} ${model.modelCode || ''} ${model.provider || ''}`.toLowerCase()
    return text.includes('embed') || text.includes('bge') || text.includes('bgem3') || text.includes('m3')
  })
  return preferred.length ? preferred : enabled
})

const semanticModels = computed(() => {
  return models.value.filter(model => model.status === 1)
})

const loadModels = async () => {
  const res = await api.getModels({ pageNum: 1, pageSize: 100 })
  models.value = res.data?.data?.records || []
  if (!form.embeddingModelId && embeddingModels.value.length) {
    form.embeddingModelId = embeddingModels.value[0].id
  }
}

const loadRecords = async () => {
  const res = await api.getRagIngestions({ pageNum: 1, pageSize: 20 })
  records.value = res.data?.data?.records || []
}

const loadCollections = async () => {
  collectionsLoading.value = true
  try {
    const res = await api.getChromaCollections({ chromaUrl: form.chromaUrl })
    collections.value = res.data?.data || []
  } catch (e) {
    collections.value = []
  } finally {
    collectionsLoading.value = false
  }
}

const loadPageData = async () => {
  loading.value = true
  try {
    await Promise.all([loadModels(), loadRecords(), loadCollections()])
  } catch (e) {
    ElMessage.error(t('rag.loadFailed'))
  } finally {
    loading.value = false
  }
}

const handleIngest = async () => {
  if (!form.collectionName || !form.content || !form.embeddingModelId) {
    ElMessage.warning(t('rag.required'))
    return
  }
  submitting.value = true
  try {
    const res = await api.createRagIngestion({ ...form })
    if (res.data?.code === 200) {
      ElMessage.success(t('rag.success'))
      form.content = ''
      await Promise.all([loadRecords(), loadCollections()])
    } else {
      ElMessage.error(res.data?.message || t('rag.failed'))
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || t('rag.failed'))
  } finally {
    submitting.value = false
  }
}

const statusType = (status) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'warning'
}

const documentsTitle = computed(() => currentCollection.value ? `${currentCollection.value.name} 文档块` : '文档块')

const openDocuments = async (collection) => {
  currentCollection.value = collection
  documentsVisible.value = true
  await loadDocuments()
}

const loadDocuments = async () => {
  if (!currentCollection.value?.id) return
  documentsLoading.value = true
  try {
    const res = await api.getChromaDocuments(currentCollection.value.id, {
      chromaUrl: form.chromaUrl,
      limit: 50,
      offset: 0
    })
    documents.value = res.data?.data?.documents || []
    documentsTotal.value = res.data?.data?.total || 0
  } catch (e) {
    documents.value = []
    documentsTotal.value = 0
    ElMessage.error(e.response?.data?.message || 'Chroma 文档块加载失败')
  } finally {
    documentsLoading.value = false
  }
}

const formatMetadata = (metadata) => {
  if (!metadata || !Object.keys(metadata).length) return '-'
  return Object.entries(metadata).map(([key, value]) => `${key}: ${value}`).join(', ')
}

onMounted(loadPageData)
</script>

<style scoped>
.rag-page {
  animation: fadeInUp 0.4s ease;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 24px;
  margin-bottom: 6px;
}

.page-header p,
.section-head span {
  color: var(--text-muted);
}

.rag-grid {
  display: grid;
  grid-template-columns: minmax(420px, 0.9fr) minmax(520px, 1.1fr);
  gap: 16px;
  align-items: start;
}

.ingest-panel,
.history-panel,
.chroma-panel {
  background: #ffffff;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 18px;
}

.chroma-panel {
  margin-top: 16px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.section-head h3 {
  font-size: 18px;
}

.section-head span {
  font-size: 12px;
  text-align: right;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.number-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.number-grid :deep(.el-input-number) {
  width: 100%;
}

.mode-control {
  width: 100%;
}

.mode-control :deep(.el-radio-button) {
  flex: 1;
}

.mode-control :deep(.el-radio-button__inner) {
  width: 100%;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
}

.empty-state {
  border: 1px dashed var(--border-color);
  border-radius: 8px;
  padding: 48px 24px;
  color: var(--text-muted);
  text-align: center;
}

.documents-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  color: var(--text-muted);
  font-size: 13px;
}

.document-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.document-item {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
  background: #f8fafc;
}

.document-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.document-head strong {
  font-family: var(--font-mono);
  font-size: 13px;
  word-break: break-all;
}

.document-meta {
  display: flex;
  gap: 12px;
  color: var(--text-muted);
  font-size: 12px;
  margin-bottom: 8px;
}

.document-item pre {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  color: #111827;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 1180px) {
  .rag-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
