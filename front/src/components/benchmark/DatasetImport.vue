<template>
  <div class="dataset-import">
    <div class="section-header">
      <h3 class="section-title">导入数据集</h3>
      <p class="section-desc">支持 CSV、JSON、JSONL、Excel、TXT、XML 格式的文件上传</p>
    </div>

    <!-- Upload Area -->
    <div
      class="upload-area"
      :class="{ 'drag-over': isDragOver }"
      @dragover.prevent="isDragOver = true"
      @dragleave.prevent="isDragOver = false"
      @drop.prevent="handleDrop"
    >
      <div class="upload-icon">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
          <polyline points="17 8 12 3 7 8" />
          <line x1="12" y1="3" x2="12" y2="15" />
        </svg>
      </div>
      <p class="upload-text">将文件拖拽到此处，或<span class="upload-link" @click="triggerFileInput">点击上传</span></p>
      <p class="upload-hint">支持 .csv, .json, .jsonl, .xlsx, .txt, .xml 文件</p>
      <input
        ref="fileInputRef"
        type="file"
        accept=".csv,.json,.jsonl,.xlsx,.txt,.xml"
        style="display: none"
        @change="handleFileChange"
      />
    </div>

    <!-- File List -->
    <div v-if="uploadedFiles.length > 0" class="file-list">
      <h4 class="list-title">已上传文件 ({{ uploadedFiles.length }})</h4>
      <div class="file-items">
        <div v-for="(file, index) in uploadedFiles" :key="index" class="file-item">
          <div class="file-icon">
            <span>{{ getFileIcon(file.name) }}</span>
          </div>
          <div class="file-info">
            <span class="file-name">{{ file.name }}</span>
            <span class="file-size mono">{{ formatFileSize(file.size) }}</span>
          </div>
          <div class="file-status">
            <span v-if="file.status === 'success'" class="status-tag success">
              <el-icon><Check /></el-icon> 解析成功
            </span>
            <span v-else-if="file.status === 'error'" class="status-tag error">
              <el-icon><Close /></el-icon> 解析失败
            </span>
            <span v-else class="status-tag loading">
              <el-icon class="is-loading"><Loading /></el-icon> 解析中...
            </span>
          </div>
          <button class="file-remove" @click="removeFile(index)">
            <el-icon><Delete /></el-icon>
          </button>
        </div>
      </div>
    </div>

    <!-- Data Preview -->
    <div v-if="previewData.length > 0" class="data-preview">
      <div class="preview-header">
        <h4 class="list-title">数据预览</h4>
        <span class="preview-count mono">共 {{ previewData.length }} 条记录</span>
      </div>
      <el-table :data="previewData" stripe max-height="300" class="preview-table">
        <el-table-column
          v-for="(col, index) in previewColumns"
          :key="index"
          :prop="col"
          :label="col"
          min-width="120"
          show-overflow-tooltip
        />
      </el-table>
    </div>

    <!-- Actions -->
    <div class="import-actions">
      <el-button @click="resetImport" class="reset-btn">重置</el-button>
      <el-button
        type="primary"
        :disabled="!canProceed"
        @click="handleProceed"
        class="proceed-btn"
      >
        下一步
        <el-icon><ArrowRight /></el-icon>
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Close, Delete, ArrowRight, Loading } from '@element-plus/icons-vue'
import api from '../../api'

const emit = defineEmits(['next'])

const fileInputRef = ref(null)
const isDragOver = ref(false)
const uploadedFiles = ref([])
const previewData = ref([])
const previewColumns = ref([])

const canProceed = computed(() => {
  return uploadedFiles.value.length > 0 && uploadedFiles.value.every(f => f.status === 'success')
})

const triggerFileInput = () => {
  fileInputRef.value?.click()
}

const handleFileChange = (event) => {
  const files = Array.from(event.target.files)
  processFiles(files)
  event.target.value = ''
}

const handleDrop = (event) => {
  isDragOver.value = false
  const files = Array.from(event.dataTransfer.files)
  processFiles(files)
}

const processFiles = (files) => {
  const validExtensions = ['.csv', '.json', '.jsonl', '.xlsx', '.txt', '.xml']
  const validFiles = files.filter(file => {
    const ext = '.' + file.name.split('.').pop().toLowerCase()
    return validExtensions.includes(ext)
  })

  if (validFiles.length !== files.length) {
    ElMessage.warning('部分文件格式不支持，已过滤')
  }

  validFiles.forEach(file => {
    const fileObj = {
      name: file.name,
      size: file.size,
      file: file,
      status: 'loading'
    }
    uploadedFiles.value.push(fileObj)
    parseFile(fileObj)
  })
}

const datasetId = ref(null)

const parseFile = async (fileObj) => {
  try {
    const formData = new FormData()
    formData.append('file', fileObj.file)

    const res = await api.uploadDataset(formData)

    if (res.data.code === 200) {
      fileObj.status = 'success'
      fileObj.datasetId = res.data.data.datasetId

      if (!datasetId.value) {
        datasetId.value = res.data.data.datasetId
      }

      if (res.data.data.preview) {
        previewData.value = res.data.data.preview
        if (previewData.value.length > 0) {
          previewColumns.value = Object.keys(previewData.value[0])
        }
      }

      ElMessage.success(`文件 ${fileObj.name} 解析成功`)
    } else {
      fileObj.status = 'error'
      ElMessage.error(`文件 ${fileObj.name} 解析失败: ${res.data.message}`)
    }
  } catch (e) {
    fileObj.status = 'error'
    ElMessage.error(`文件 ${fileObj.name} 上传失败`)
  }
}

const removeFile = (index) => {
  uploadedFiles.value.splice(index, 1)
  if (uploadedFiles.value.length === 0) {
    previewData.value = []
    previewColumns.value = []
  }
}

const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const getFileIcon = (filename) => {
  const ext = filename.split('.').pop().toLowerCase()
  const icons = {
    csv: '📄',
    json: '📋',
    jsonl: '📋',
    xlsx: '📊',
    txt: '📝',
    xml: '📰'
  }
  return icons[ext] || '📁'
}

const resetImport = () => {
  uploadedFiles.value = []
  previewData.value = []
  previewColumns.value = []
  datasetId.value = null
}

const handleProceed = () => {
  if (!datasetId.value && uploadedFiles.value.length > 0) {
    const successFile = uploadedFiles.value.find(f => f.status === 'success')
    if (successFile) {
      datasetId.value = successFile.datasetId
    }
  }

  if (datasetId.value) {
    emit('next', { datasetId: datasetId.value })
  } else {
    ElMessage.warning('请等待文件解析完成')
  }
}
</script>

<style scoped>
.dataset-import {
  max-width: 900px;
}

.section-header {
  margin-bottom: 24px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.section-desc {
  font-size: 14px;
  color: var(--text-muted);
}

.upload-area {
  border: 2px dashed var(--border-color);
  border-radius: 16px;
  padding: 48px 24px;
  text-align: center;
  background: var(--glass-bg);
  transition: all 0.3s ease;
  cursor: pointer;
}

.upload-area:hover,
.upload-area.drag-over {
  border-color: var(--neon-cyan);
  background: rgba(0, 212, 255, 0.05);
}

.upload-icon {
  color: var(--text-muted);
  margin-bottom: 16px;
}

.upload-text {
  font-size: 16px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.upload-link {
  color: var(--neon-cyan);
  cursor: pointer;
  text-decoration: underline;
}

.upload-hint {
  font-size: 12px;
  color: var(--text-muted);
}

.file-list {
  margin-top: 24px;
}

.list-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.file-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--glass-bg);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.file-icon {
  font-size: 24px;
}

.file-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.file-size {
  font-size: 12px;
  color: var(--text-muted);
}

.file-status {
  display: flex;
  align-items: center;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
}

.status-tag.success {
  background: rgba(0, 255, 136, 0.15);
  color: var(--neon-green);
}

.status-tag.error {
  background: rgba(239, 68, 68, 0.15);
  color: var(--neon-pink);
}

.status-tag.loading {
  background: rgba(59, 130, 246, 0.15);
  color: #3b82f6;
}

.file-remove {
  padding: 8px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  border-radius: 8px;
}

.file-remove:hover {
  background: rgba(239, 68, 68, 0.1);
  color: var(--neon-pink);
}

.data-preview {
  margin-top: 24px;
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.preview-count {
  font-size: 12px;
  color: var(--text-muted);
}

.preview-table {
  border-radius: 12px;
}

.import-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--border-color);
}

.reset-btn {
  padding: 12px 24px;
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  border-radius: 10px;
}

.proceed-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 32px;
  background: linear-gradient(135deg, var(--neon-cyan), var(--neon-purple));
  border: none;
  color: #000;
  font-weight: 600;
  border-radius: 10px;
}

.proceed-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>