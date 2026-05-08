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

const form = reactive({
  collectionName: 'default_knowledge',
  documentTitle: '',
  embeddingModelId: null,
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

const loadPageData = async () => {
  loading.value = true
  try {
    await Promise.all([loadModels(), loadRecords()])
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
      await loadRecords()
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
.history-panel {
  background: #ffffff;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 18px;
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

@media (max-width: 1180px) {
  .rag-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
