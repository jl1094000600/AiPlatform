<template>
  <div class="admin-page">
    <header class="page-head">
      <div>
        <h2>租户管理</h2>
        <p>管理平台中的企业、团队和默认租户空间。</p>
      </div>
      <el-button type="primary" @click="openDialog()">新建租户</el-button>
    </header>

    <el-card shadow="never">
      <el-table :data="tenants" stripe v-loading="loading">
        <el-table-column prop="tenantName" label="租户名称" min-width="160" />
        <el-table-column prop="tenantCode" label="租户编码" min-width="140" />
        <el-table-column prop="contactName" label="联系人" width="120" />
        <el-table-column prop="contactEmail" label="联系邮箱" min-width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.form.id ? '编辑租户' : '新建租户'" width="560px">
      <el-form label-position="top">
        <el-form-item label="租户名称" required>
          <el-input v-model="dialog.form.tenantName" />
        </el-form-item>
        <el-form-item label="租户编码" required>
          <el-input v-model="dialog.form.tenantCode" :disabled="!!dialog.form.id" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="dialog.form.contactName" />
        </el-form-item>
        <el-form-item label="联系邮箱">
          <el-input v-model="dialog.form.contactEmail" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="dialog.form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveTenant">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const loading = ref(false)
const tenants = ref([])
const dialog = reactive({ visible: false, form: defaultForm() })

function defaultForm() {
  return { id: null, tenantCode: '', tenantName: '', contactName: '', contactEmail: '', status: 1 }
}

async function loadTenants() {
  loading.value = true
  try {
    const res = await api.getTenants({ pageNum: 1, pageSize: 100 })
    tenants.value = res.data.data?.records || []
  } finally {
    loading.value = false
  }
}

function openDialog(row = null) {
  dialog.form = row ? { ...defaultForm(), ...row } : defaultForm()
  dialog.visible = true
}

async function saveTenant() {
  if (!dialog.form.tenantName || !dialog.form.tenantCode) {
    ElMessage.warning('请填写租户名称和编码')
    return
  }
  if (dialog.form.id) {
    await api.updateTenant(dialog.form.id, dialog.form)
  } else {
    await api.createTenant(dialog.form)
  }
  ElMessage.success('租户已保存')
  dialog.visible = false
  await loadTenants()
}

onMounted(loadTenants)
</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.page-head h2 {
  margin: 0;
}

.page-head p {
  margin: 6px 0 0;
  color: #64748b;
}
</style>
