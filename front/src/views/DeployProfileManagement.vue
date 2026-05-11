<template>
  <div class="deploy-page">
    <div class="page-head">
      <div>
        <h2>部署配置</h2>
        <p>配置代码生成后的 Docker 或 Jenkins 自动部署方式。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="loadProfiles">刷新</el-button>
        <el-button type="primary" @click="openCreate">新增配置</el-button>
      </div>
    </div>

    <section class="panel">
      <el-table :data="profiles" v-loading="loading" stripe>
        <el-table-column prop="profileName" label="配置名称" min-width="180" />
        <el-table-column prop="deployType" label="方式" width="110" />
        <el-table-column prop="environmentName" label="环境" width="110" />
        <el-table-column prop="healthCheckUrl" label="健康检查" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="deleteProfile(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑部署配置' : '新增部署配置'" width="760px">
      <el-form :model="form" label-position="top">
        <div class="form-grid">
          <el-form-item label="配置名称" required>
            <el-input v-model="form.profileName" placeholder="dev docker deploy" />
          </el-form-item>
          <el-form-item label="部署方式" required>
            <el-select v-model="form.deployType" style="width: 100%">
              <el-option label="Docker" value="DOCKER" />
              <el-option label="Jenkins" value="JENKINS" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item label="环境">
            <el-input v-model="form.environmentName" placeholder="dev" />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="enabled" active-text="启用" inactive-text="禁用" />
          </el-form-item>
        </div>
        <el-form-item label="构建命令">
          <el-input v-model="form.buildCommand" placeholder="npm.cmd run build 或 mvn package" />
        </el-form-item>
        <el-form-item label="测试命令">
          <el-input v-model="form.testCommand" placeholder="npm.cmd test 或 mvn test" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="健康检查 URL">
            <el-input v-model="form.healthCheckUrl" placeholder="http://localhost:8080/actuator/health" />
          </el-form-item>
          <el-form-item label="超时时间（秒）">
            <el-input-number v-model="form.timeoutSeconds" :min="10" :max="7200" :step="30" />
          </el-form-item>
        </div>

        <el-form-item v-if="form.deployType === 'DOCKER'" label="Docker 配置 JSON">
          <el-input v-model="form.dockerConfig" type="textarea" :rows="9" />
        </el-form-item>
        <el-form-item v-else label="Jenkins 配置 JSON">
          <el-input v-model="form.jenkinsConfig" type="textarea" :rows="9" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const loading = ref(false)
const profiles = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)

const defaultDockerConfig = () => JSON.stringify({
  dockerMode: 'BUILD_RUN',
  dockerfilePath: 'Dockerfile',
  buildContext: '.',
  imageName: 'aipal-generated',
  tagStrategy: 'PIPELINE_ID',
  containerName: 'aipal-generated',
  ports: ['8080:8080'],
  envVars: {}
}, null, 2)

const defaultJenkinsConfig = () => JSON.stringify({
  jenkinsUrl: 'http://localhost:8081',
  jobName: 'ai-platform-deploy',
  username: '',
  apiToken: '',
  buildToken: '',
  parametersJson: '{"PIPELINE_ID":"","ENVIRONMENT":"dev"}',
  pollIntervalSeconds: 5
}, null, 2)

const form = reactive({
  profileName: '',
  deployType: 'DOCKER',
  environmentName: 'dev',
  status: 1,
  buildCommand: '',
  testCommand: '',
  healthCheckUrl: '',
  timeoutSeconds: 600,
  dockerConfig: defaultDockerConfig(),
  jenkinsConfig: defaultJenkinsConfig()
})

const enabled = computed({
  get: () => form.status === 1,
  set: value => { form.status = value ? 1 : 0 }
})

const loadProfiles = async () => {
  loading.value = true
  try {
    const res = await api.getAutomationDeployProfiles({ pageNum: 1, pageSize: 50 })
    profiles.value = res.data?.data?.records || []
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  Object.assign(form, {
    profileName: '',
    deployType: 'DOCKER',
    environmentName: 'dev',
    status: 1,
    buildCommand: '',
    testCommand: '',
    healthCheckUrl: '',
    timeoutSeconds: 600,
    dockerConfig: defaultDockerConfig(),
    jenkinsConfig: defaultJenkinsConfig()
  })
}

const openCreate = () => {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

const openEdit = async (row) => {
  const res = await api.getAutomationDeployProfile(row.id)
  const data = res.data?.data || row
  editingId.value = data.id
  Object.assign(form, {
    profileName: data.profileName || '',
    deployType: data.deployType || 'DOCKER',
    environmentName: data.environmentName || 'dev',
    status: data.status ?? 1,
    buildCommand: data.buildCommand || '',
    testCommand: data.testCommand || '',
    healthCheckUrl: data.healthCheckUrl || '',
    timeoutSeconds: data.timeoutSeconds || 600,
    dockerConfig: data.dockerConfig || defaultDockerConfig(),
    jenkinsConfig: data.jenkinsConfig || defaultJenkinsConfig()
  })
  dialogVisible.value = true
}

const validateJson = (value, label) => {
  if (!value) return
  try {
    JSON.parse(value)
  } catch {
    throw new Error(`${label} 必须是合法 JSON`)
  }
}

const saveProfile = async () => {
  if (!form.profileName) {
    ElMessage.warning('请填写配置名称')
    return
  }
  try {
    validateJson(form.dockerConfig, 'Docker 配置')
    validateJson(form.jenkinsConfig, 'Jenkins 配置')
    const payload = { ...form }
    if (payload.deployType === 'DOCKER') payload.jenkinsConfig = ''
    if (payload.deployType === 'JENKINS') payload.dockerConfig = ''
    if (editingId.value) {
      await api.updateAutomationDeployProfile(editingId.value, payload)
    } else {
      await api.createAutomationDeployProfile(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadProfiles()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '保存失败')
  }
}

const deleteProfile = async (row) => {
  await ElMessageBox.confirm(`删除部署配置「${row.profileName}」？`, '删除确认', { type: 'warning' })
  await api.deleteAutomationDeployProfile(row.id)
  ElMessage.success('删除成功')
  await loadProfiles()
}

onMounted(loadProfiles)
</script>

<style scoped>
.deploy-page { color: var(--text-primary); }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; }
.page-head h2 { font-size: 24px; margin-bottom: 6px; }
.page-head p { color: var(--text-muted); }
.head-actions { display: flex; gap: 10px; }
.panel { background: #ffffff; border: 1px solid var(--border-color); border-radius: 8px; padding: 16px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
:deep(.el-input-number) { width: 100%; }
@media (max-width: 900px) {
  .page-head, .form-grid { grid-template-columns: 1fr; flex-direction: column; }
}
</style>
