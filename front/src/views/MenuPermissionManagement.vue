<template>
  <div class="admin-page">
    <header class="page-head">
      <div>
        <h2>菜单权限</h2>
        <p>维护侧边栏菜单、路由和菜单绑定的权限码。</p>
      </div>
      <el-button type="primary" @click="openDialog()">新建菜单</el-button>
    </header>

    <el-card shadow="never">
      <el-table
        :data="menuTree"
        row-key="id"
        stripe
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="menuName" label="菜单名称" min-width="140" />
        <el-table-column prop="menuCode" label="编码" min-width="140" />
        <el-table-column prop="path" label="路由" min-width="160" />
        <el-table-column prop="icon" label="图标" width="120" />
        <el-table-column prop="permissionCode" label="权限码" min-width="180" />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.form.id ? '编辑菜单' : '新建菜单'" width="560px">
      <el-form label-position="top">
        <el-form-item label="菜单名称" required>
          <el-input v-model="dialog.form.menuName" />
        </el-form-item>
        <el-form-item label="菜单编码" required>
          <el-input v-model="dialog.form.menuCode" />
        </el-form-item>
        <el-form-item label="路由">
          <el-input v-model="dialog.form.path" />
        </el-form-item>
        <el-form-item label="父级菜单">
          <el-select v-model="dialog.form.parentId" clearable placeholder="不选择则作为一级菜单" style="width: 100%">
            <el-option
              v-for="menu in parentMenuOptions"
              :key="menu.id"
              :label="menu.menuName"
              :value="menu.id"
              :disabled="menu.id === dialog.form.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="dialog.form.icon" />
        </el-form-item>
        <el-form-item label="权限码">
          <el-input v-model="dialog.form.permissionCode" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dialog.form.sortOrder" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveMenu">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const menus = ref([])
const dialog = reactive({ visible: false, form: defaultForm() })

const menuTree = computed(() => buildMenuTree(menus.value))
const parentMenuOptions = computed(() => menus.value.filter(menu => !menu.parentId))

function defaultForm() {
  return { id: null, menuCode: '', menuName: '', path: '', icon: 'MagicStick', permissionCode: '', parentId: null, sortOrder: 0, visible: 1, status: 1 }
}

async function loadMenus() {
  const res = await api.getTenantMenus()
  menus.value = res.data.data || []
}

function openDialog(row = null) {
  dialog.form = row ? { ...defaultForm(), ...row } : defaultForm()
  dialog.visible = true
}

function buildMenuTree(items) {
  const byId = new Map()
  const roots = []
  items.forEach(item => byId.set(item.id, { ...item, children: [] }))
  byId.forEach(item => {
    if (item.parentId && byId.has(item.parentId)) {
      byId.get(item.parentId).children.push(item)
    } else {
      roots.push(item)
    }
  })
  const sortMenus = list => {
    list.sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    list.forEach(item => sortMenus(item.children || []))
    return list
  }
  return sortMenus(roots)
}

async function saveMenu() {
  await api.saveTenantMenu(dialog.form)
  ElMessage.success('菜单已保存')
  dialog.visible = false
  await loadMenus()
}

onMounted(loadMenus)
</script>

<style scoped>
.admin-page { display: flex; flex-direction: column; gap: 16px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; }
.page-head h2 { margin: 0; }
.page-head p { margin: 6px 0 0; color: #64748b; }
</style>
