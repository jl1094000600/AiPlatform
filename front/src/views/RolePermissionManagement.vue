<template>
  <div class="admin-page">
    <header class="page-head">
      <div>
        <h2>角色权限</h2>
        <p>为租户角色配置可访问的菜单和接口权限。</p>
      </div>
    </header>

    <div class="split">
      <el-card shadow="never">
        <template #header>角色</template>
        <el-table :data="roles" highlight-current-row @current-change="selectRole">
          <el-table-column prop="roleName" label="角色名称" />
          <el-table-column prop="roleCode" label="编码" />
        </el-table>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-head">
            <span>权限配置</span>
            <el-button type="primary" :disabled="!selectedRole" @click="savePermissions">保存</el-button>
          </div>
        </template>
        <el-checkbox-group v-model="selectedPermissionIds">
          <div class="permission-grid">
            <el-checkbox v-for="permission in permissions" :key="permission.id" :label="permission.id">
              {{ permission.permissionName }} / {{ permission.permissionCode }}
            </el-checkbox>
          </div>
        </el-checkbox-group>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const roles = ref([])
const permissions = ref([])
const selectedRole = ref(null)
const selectedPermissionIds = ref([])

async function loadAll() {
  const [roleRes, permissionRes] = await Promise.all([
    api.getTenantRoles(),
    api.getTenantPermissions()
  ])
  roles.value = roleRes.data.data || []
  permissions.value = permissionRes.data.data || []
}

async function selectRole(role) {
  selectedRole.value = role
  selectedPermissionIds.value = []
  if (!role?.id) return
  const res = await api.getRolePermissions(role.id)
  selectedPermissionIds.value = res.data.data || []
}

async function savePermissions() {
  await api.updateRolePermissions(selectedRole.value.id, selectedPermissionIds.value)
  ElMessage.success('权限已保存')
}

onMounted(loadAll)
</script>

<style scoped>
.admin-page { display: flex; flex-direction: column; gap: 16px; }
.page-head h2 { margin: 0; }
.page-head p { margin: 6px 0 0; color: #64748b; }
.split { display: grid; grid-template-columns: 360px 1fr; gap: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.permission-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 18px; }
</style>
