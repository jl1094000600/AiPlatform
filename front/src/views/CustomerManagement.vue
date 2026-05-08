<template>
  <div class="ops-page">
    <div class="ops-header">
      <div>
        <h2>客户账户管理</h2>
        <span class="muted">客户、余额、冻结和扣费策略</span>
      </div>
      <el-button type="primary" @click="openDialog()">新增客户</el-button>
    </div>

    <div class="filter-row">
      <el-input v-model="keyword" placeholder="客户名称/编码" clearable />
      <el-button @click="loadCustomers">查询</el-button>
    </div>

    <section class="ops-panel">
      <el-table :data="customers" v-loading="loading">
        <el-table-column prop="customerCode" label="编码" width="160" />
        <el-table-column prop="customerName" label="客户名称" min-width="180" />
        <el-table-column prop="contactName" label="联系人" width="120" />
        <el-table-column prop="balance" label="余额" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '正常' : '冻结' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="280">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" @click="openBalance(row)">调账</el-button>
            <el-button size="small" type="warning" @click="freeze(row)">冻结</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="showDialog" :title="form.id ? '编辑客户' : '新增客户'" width="520px">
      <el-form :model="form" label-position="top">
        <el-form-item label="客户编码"><el-input v-model="form.customerCode" /></el-form-item>
        <el-form-item label="客户名称"><el-input v-model="form.customerName" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.contactEmail" /></el-form-item>
        <el-form-item label="余额"><el-input-number v-model="form.balance" :min="-999999" /></el-form-item>
        <el-form-item label="预警余额"><el-input-number v-model="form.warningBalance" :min="0" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showDialog = false">取消</el-button><el-button type="primary" @click="saveCustomer">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="showBalanceDialog" title="余额调整" width="420px">
      <el-form :model="balanceForm" label-position="top">
        <el-form-item label="类型"><el-select v-model="balanceForm.type"><el-option label="充值" value="RECHARGE" /><el-option label="扣费" value="DEDUCT" /></el-select></el-form-item>
        <el-form-item label="金额"><el-input-number v-model="balanceForm.amount" :min="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="balanceForm.remark" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showBalanceDialog = false">取消</el-button><el-button type="primary" @click="adjustBalance">确认</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const customers = ref([])
const loading = ref(false)
const keyword = ref('')
const showDialog = ref(false)
const showBalanceDialog = ref(false)
const form = reactive({})
const balanceForm = reactive({ customerId: null, type: 'RECHARGE', amount: 100, remark: '' })

const loadCustomers = async () => {
  loading.value = true
  try {
    const res = await api.getCustomers({ pageNum: 1, pageSize: 50, keyword: keyword.value || undefined })
    customers.value = res.data.data?.records || []
  } finally {
    loading.value = false
  }
}

const openDialog = (row) => {
  Object.keys(form).forEach(k => delete form[k])
  Object.assign(form, row || { status: 1, balance: 0, warningBalance: 100 })
  showDialog.value = true
}

const saveCustomer = async () => {
  if (form.id) await api.updateCustomer(form.id, form)
  else await api.createCustomer(form)
  showDialog.value = false
  ElMessage.success('客户已保存')
  loadCustomers()
}

const openBalance = (row) => {
  balanceForm.customerId = row.id
  showBalanceDialog.value = true
}

const adjustBalance = async () => {
  await api.adjustCustomerBalance(balanceForm.customerId, balanceForm)
  showBalanceDialog.value = false
  ElMessage.success('余额已调整')
  loadCustomers()
}

const freeze = async (row) => {
  await api.freezeCustomer(row.id)
  ElMessage.success('客户已冻结')
  loadCustomers()
}

onMounted(loadCustomers)
</script>

<style scoped>
.ops-page { padding: 24px; color: var(--text-primary); }
.ops-header, .filter-row { display: flex; align-items: center; gap: 12px; }
.ops-header { justify-content: space-between; margin-bottom: 18px; }
.ops-header h2 { font-size: 26px; margin-bottom: 6px; }
.muted { color: var(--text-muted); }
.filter-row { margin-bottom: 18px; }
.filter-row .el-input { width: 240px; }
.ops-panel { background: var(--glass-bg); border: 1px solid var(--glass-border); border-radius: 8px; padding: 18px; }
</style>
