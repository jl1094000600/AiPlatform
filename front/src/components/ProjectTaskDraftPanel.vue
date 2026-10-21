<template>
  <section class="task-draft-panel">
    <div class="panel-head">
      <strong>任务草稿</strong>
      <div>
        <el-button size="small" :loading="loading" @click="$emit('refresh')">刷新</el-button>
        <el-button size="small" type="primary" @click="openCreate">新建草稿</el-button>
      </div>
    </div>
    <p class="hint">草稿需显式确认后才会创建 Run；确认携带 expectedVersion，冲突可解释。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-table :data="drafts" v-loading="loading" empty-text="暂无草稿" row-key="id">
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.status === 'CONFIRMED' ? 'success' : row.status === 'CANCELLED' ? 'info' : 'warning'">
            {{ statusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="70" />
      <el-table-column label="操作" min-width="180">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button v-if="row.status === 'DRAFT' || row.status === 'PENDING'" size="small" type="primary" :disabled="busy" @click="confirm(row)">确认执行</el-button>
            <el-button v-if="row.status !== 'CANCELLED' && row.status !== 'CONFIRMED'" size="small" :disabled="busy" @click="cancel(row)">取消</el-button>
            <span v-if="row.confirmedRunId">Run #{{ row.confirmedRunId }}</span>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" title="新建任务草稿" width="480px">
      <el-form label-width="96px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="2000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" :disabled="!form.title.trim()" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'

const props = defineProps({
  projectKey: { type: String, default: '' },
  drafts: { type: Array, default: () => [] },
  loading: Boolean,
  busy: Boolean,
  error: { type: String, default: '' }
})
const emit = defineEmits(['refresh', 'create', 'confirm', 'cancel'])
const createVisible = ref(false)
const form = reactive({ title: '', description: '' })

function statusText(status) {
  const map = {
    DRAFT: '草稿',
    PENDING: '待确认',
    CONFIRMED: '已确认',
    CANCELLED: '已取消'
  }
  return map[status] || status || '未知'
}

function openCreate() {
  form.title = ''
  form.description = ''
  createVisible.value = true
}

function submitCreate() {
  emit('create', { title: form.title.trim(), description: form.description.trim() })
  createVisible.value = false
}

function confirm(row) {
  emit('confirm', row)
}

function cancel(row) {
  emit('cancel', row)
}
</script>

<style scoped>
.task-draft-panel {
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  padding: 12px 14px;
  background: var(--el-bg-color);
}
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin: 0 0 8px;
}
.row-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
