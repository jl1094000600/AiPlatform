<template>
  <div class="skill-page">
    <div class="page-header">
      <div>
        <h2>Skill 管理</h2>
        <p>维护可复用的生成约束、提示词和 Java 可读取的函数元数据。</p>
      </div>
      <el-button type="primary" @click="openDialog()">
        <Plus class="btn-icon" /> 新建 Skill
      </el-button>
    </div>

    <section class="panel">
      <el-table :data="skills" v-loading="loading" stripe>
        <el-table-column prop="skillName" label="名称" min-width="180" />
        <el-table-column prop="skillCode" label="编码" min-width="170" />
        <el-table-column prop="description" label="描述" min-width="240" show-overflow-tooltip />
        <el-table-column label="函数" width="90">
          <template #default="{ row }">{{ row.functionDefinitions?.length || 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190">
          <template #default="{ row }">
            <el-button size="small" @click.stop="openDialog(row)">
              <Edit /> 编辑
            </el-button>
            <el-button size="small" type="danger" plain @click.stop="handleDelete(row)">
              <Delete /> 删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper" v-if="total > pageSize">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadSkills"
        />
      </div>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑 Skill' : '新建 Skill'"
      width="900px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-position="top" class="skill-form">
        <div class="form-grid">
          <el-form-item label="名称" required>
            <el-input v-model="form.skillName" placeholder="Java Domain Helper" />
          </el-form-item>
          <el-form-item label="编码">
            <el-input v-model="form.skillCode" placeholder="留空自动生成" :disabled="!!form.id" />
          </el-form-item>
        </div>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="描述这个 Skill 适合的场景" />
        </el-form-item>
        <el-form-item label="提示词内容">
          <el-input v-model="form.promptContent" type="textarea" :rows="5" placeholder="写入模型需要遵循的业务、技术或风格约束" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>

        <div class="function-head">
          <strong>函数元数据</strong>
          <el-button size="small" @click="addFunction">
            <Plus /> 添加函数
          </el-button>
        </div>

        <div v-if="form.functionDefinitions.length" class="function-list">
          <div v-for="(fn, index) in form.functionDefinitions" :key="index" class="function-item">
            <div class="function-title">
              <el-input v-model="fn.name" placeholder="函数名，例如 readCustomer" />
              <el-switch v-model="fn.enabled" active-text="启用" inactive-text="禁用" />
              <el-button text type="danger" @click="removeFunction(index)">
                <Delete />
              </el-button>
            </div>
            <el-input v-model="fn.description" placeholder="函数描述" />
            <div class="form-grid">
              <el-input v-model="fn.parametersJson" type="textarea" :rows="4" placeholder='参数 JSON，例如 {"type":"object","properties":{"id":{"type":"integer"}}}' />
              <el-input v-model="fn.returnSchema" type="textarea" :rows="4" placeholder="返回说明或 JSON Schema" />
            </div>
            <el-input v-model="fn.javaSnippet" type="textarea" :rows="5" placeholder="Java 可读取的函数签名或代码片段，不会被动态执行" />
          </div>
        </div>
        <div v-else class="empty-functions">暂无函数元数据</div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus } from '@element-plus/icons-vue'
import api from '../api'

const loading = ref(false)
const dialogVisible = ref(false)
const enabled = ref(true)
const skills = ref([])
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const form = reactive(defaultForm())

function defaultForm() {
  return {
    id: null,
    skillCode: '',
    skillName: '',
    description: '',
    status: 1,
    promptContent: '',
    functionDefinitions: []
  }
}

const loadSkills = async () => {
  loading.value = true
  try {
    const res = await api.getSkills({ pageNum: pageNum.value, pageSize: pageSize.value })
    skills.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } catch (error) {
    ElMessage.error('Skill 列表加载失败')
  } finally {
    loading.value = false
  }
}

const openDialog = (skill = null) => {
  Object.assign(form, skill ? { ...defaultForm(), ...skill } : defaultForm())
  form.functionDefinitions = (skill?.functionDefinitions || []).map(item => ({
    name: item.name || '',
    description: item.description || '',
    parametersJson: item.parametersJson || '',
    returnSchema: item.returnSchema || '',
    javaSnippet: item.javaSnippet || '',
    enabled: item.enabled !== false
  }))
  enabled.value = form.status !== 0
  dialogVisible.value = true
}

const addFunction = () => {
  form.functionDefinitions.push({
    name: '',
    description: '',
    parametersJson: '',
    returnSchema: '',
    javaSnippet: '',
    enabled: true
  })
}

const removeFunction = (index) => {
  form.functionDefinitions.splice(index, 1)
}

const handleSave = async () => {
  if (!form.skillName.trim()) {
    ElMessage.warning('请填写 Skill 名称')
    return
  }
  for (const fn of form.functionDefinitions) {
    if (fn.enabled !== false && !fn.name.trim()) {
      ElMessage.warning('启用函数必须填写函数名')
      return
    }
    if (fn.parametersJson?.trim()) {
      try {
        JSON.parse(fn.parametersJson)
      } catch {
        ElMessage.warning(`函数 ${fn.name || '-'} 的参数 JSON 不合法`)
        return
      }
    }
  }
  const payload = {
    ...form,
    status: enabled.value ? 1 : 0
  }
  try {
    if (form.id) {
      await api.updateSkill(form.id, payload)
    } else {
      await api.createSkill(payload)
    }
    ElMessage.success('Skill 已保存')
    dialogVisible.value = false
    await loadSkills()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || 'Skill 保存失败')
  }
}

const handleDelete = async (skill) => {
  try {
    await ElMessageBox.confirm(`确认删除 Skill「${skill.skillName}」？`, '删除确认', { type: 'warning' })
    await api.deleteSkill(skill.id)
    ElMessage.success('Skill 已删除')
    await loadSkills()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('Skill 删除失败')
    }
  }
}

onMounted(loadSkills)
</script>

<style scoped>
.skill-page {
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

.page-header p {
  color: var(--text-muted);
}

.btn-icon {
  width: 16px;
  height: 16px;
}

.panel {
  background: #ffffff;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 16px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}

.skill-form {
  padding-top: 4px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.function-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 10px 0 12px;
}

.function-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 430px;
  overflow: auto;
  padding-right: 4px;
}

.function-item {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #f8fafc;
}

.function-title {
  display: grid;
  grid-template-columns: 1fr 120px 44px;
  gap: 10px;
  align-items: center;
}

.empty-functions {
  border: 1px dashed var(--border-color);
  border-radius: 8px;
  padding: 24px;
  color: var(--text-muted);
  text-align: center;
}

@media (max-width: 900px) {
  .form-grid,
  .function-title {
    grid-template-columns: 1fr;
  }
}
</style>
