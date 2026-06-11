<template>
  <div class="admin-page">
    <header class="page-head">
      <div>
        <h2>成员管理</h2>
        <p>查看当前租户成员，并为成员分配租户角色。</p>
      </div>
      <el-button type="primary" @click="openDialog">添加成员</el-button>
    </header>

    <el-card shadow="never">
      <el-table :data="members" stripe v-loading="loading">
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="department" label="部门" min-width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRoleDialog(row)">分配角色</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog.visible" title="添加成员" width="520px">
      <el-form label-position="top">
        <el-form-item label="用户 ID" required>
          <el-input-number v-model="dialog.userId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="租户角色">
          <el-input v-model="dialog.tenantRole" placeholder="member/admin" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="dialog.roleIds" multiple style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :label="role.roleName" :value="role.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="addMember">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialog.visible" title="分配角色" width="520px">
      <el-select v-model="roleDialog.roleIds" multiple style="width: 100%">
        <el-option v-for="role in roles" :key="role.id" :label="role.roleName" :value="role.id" />
      </el-select>
      <template #footer>
        <el-button @click="roleDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const loading = ref(false)
const members = ref([])
const roles = ref([])
const dialog = reactive({ visible: false, userId: null, tenantRole: 'member', roleIds: [] })
const roleDialog = reactive({ visible: false, userId: null, roleIds: [] })

async function loadAll() {
  loading.value = true
  try {
    const [memberRes, roleRes] = await Promise.all([
      api.getTenantMembers({ pageNum: 1, pageSize: 100 }),
      api.getTenantRoles()
    ])
    members.value = memberRes.data.data?.records || []
    roles.value = roleRes.data.data || []
  } finally {
    loading.value = false
  }
}

function openDialog() {
  Object.assign(dialog, { visible: true, userId: null, tenantRole: 'member', roleIds: [] })
}

async function addMember() {
  await api.addTenantMember({ userId: dialog.userId, tenantRole: dialog.tenantRole, roleIds: dialog.roleIds })
  ElMessage.success('成员已添加')
  dialog.visible = false
  await loadAll()
}

async function openRoleDialog(row) {
  Object.assign(roleDialog, { visible: true, userId: row.id, roleIds: [] })
  const res = await api.getTenantMemberRoles(row.id)
  roleDialog.roleIds = res.data.data || []
}

async function saveRoles() {
  await api.updateTenantMemberRoles(roleDialog.userId, roleDialog.roleIds)
  ElMessage.success('角色已更新')
  roleDialog.visible = false
}

onMounted(loadAll)
</script>

<style scoped>
.admin-page { display: flex; flex-direction: column; gap: 16px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; }
.page-head h2 { margin: 0; }
.page-head p { margin: 6px 0 0; color: #64748b; }
</style>
