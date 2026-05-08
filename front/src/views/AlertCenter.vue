<template>
  <div class="ops-page">
    <div class="ops-header">
      <div>
        <h2>告警与应急响应</h2>
        <span class="muted">配置阈值、查看事件、确认处理状态</span>
      </div>
      <div class="actions">
        <el-button @click="evaluateRules">立即检测</el-button>
        <el-button type="primary" @click="showRuleDialog = true">新增规则</el-button>
      </div>
    </div>

    <div class="ops-grid two">
      <section class="ops-panel">
        <div class="panel-title">告警事件</div>
        <el-table :data="events" v-loading="loading">
          <el-table-column prop="level" label="级别" width="80">
            <template #default="{ row }"><el-tag :type="row.level === 'P0' ? 'danger' : 'warning'">{{ row.level }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="message" label="事件" min-width="220" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="triggerTime" label="触发时间" width="180" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" @click="ack(row)" :disabled="row.status === 'ACKED'">确认</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="ops-panel">
        <div class="panel-title">告警规则</div>
        <div class="rule-list">
          <div v-for="rule in rules" :key="rule.id" class="rule-card">
            <strong>{{ rule.ruleName }}</strong>
            <span>{{ metricText(rule.metricType) }} {{ rule.operator }} {{ rule.thresholdValue }}</span>
            <small>{{ rule.level }} · {{ rule.notifyChannel || '事件记录' }}</small>
            <el-button text type="danger" @click="deleteRule(rule)">删除</el-button>
          </div>
        </div>
      </section>
    </div>

    <el-dialog v-model="showRuleDialog" title="新增告警规则" width="520px">
      <el-form :model="ruleForm" label-position="top">
        <el-form-item label="规则名称"><el-input v-model="ruleForm.ruleName" /></el-form-item>
        <el-form-item label="指标">
          <el-select v-model="ruleForm.metricType" style="width: 100%">
            <el-option label="错误率" value="error_rate" />
            <el-option label="响应时间" value="response_time" />
            <el-option label="在线Agent数" value="offline_agents" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件">
          <div class="inline-fields">
            <el-select v-model="ruleForm.operator"><el-option label=">" value=">" /><el-option label="<" value="<" /></el-select>
            <el-input-number v-model="ruleForm.thresholdValue" :min="0" />
          </div>
        </el-form-item>
        <el-form-item label="级别"><el-select v-model="ruleForm.level"><el-option label="P0紧急" value="P0" /><el-option label="P1重要" value="P1" /><el-option label="P2提示" value="P2" /></el-select></el-form-item>
        <el-form-item label="通知渠道"><el-input v-model="ruleForm.notifyChannel" placeholder="dingTalk/email/webhook" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRuleDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const loading = ref(false)
const events = ref([])
const rules = ref([])
const showRuleDialog = ref(false)
const ruleForm = reactive({ ruleName: '错误率告警', metricType: 'error_rate', operator: '>', thresholdValue: 5, level: 'P1', notifyChannel: 'webhook', status: 1 })

const metricText = (value) => ({ error_rate: '错误率', response_time: '响应时间', offline_agents: '在线Agent数' }[value] || value)

const loadAll = async () => {
  loading.value = true
  try {
    const [eventsRes, rulesRes] = await Promise.all([
      api.getAlertEvents({ pageNum: 1, pageSize: 20 }),
      api.getAlertRules({ pageNum: 1, pageSize: 20 })
    ])
    events.value = eventsRes.data.data?.records || []
    rules.value = rulesRes.data.data?.records || []
  } finally {
    loading.value = false
  }
}

const saveRule = async () => {
  await api.createAlertRule(ruleForm)
  showRuleDialog.value = false
  ElMessage.success('告警规则已创建')
  loadAll()
}

const deleteRule = async (rule) => {
  await api.deleteAlertRule(rule.id)
  ElMessage.success('规则已删除')
  loadAll()
}

const ack = async (event) => {
  await api.ackAlertEvent(event.id)
  ElMessage.success('已确认')
  loadAll()
}

const evaluateRules = async () => {
  const res = await api.evaluateAlerts()
  ElMessage.success(`检测完成，新增 ${res.data.data || 0} 条事件`)
  loadAll()
}

onMounted(loadAll)
</script>

<style scoped>
.ops-page { padding: 24px; color: var(--text-primary); }
.ops-header, .actions { display: flex; align-items: center; gap: 12px; }
.ops-header { justify-content: space-between; margin-bottom: 18px; }
.ops-header h2 { font-size: 26px; margin-bottom: 6px; }
.muted { color: var(--text-muted); }
.ops-grid.two { display: grid; grid-template-columns: 1.4fr 1fr; gap: 18px; }
.ops-panel { background: var(--glass-bg); border: 1px solid var(--glass-border); border-radius: 8px; padding: 18px; }
.panel-title { font-weight: 700; margin-bottom: 14px; }
.rule-list { display: flex; flex-direction: column; gap: 12px; }
.rule-card { display: grid; gap: 6px; padding: 12px; border: 1px solid var(--border-color); border-radius: 8px; }
.rule-card span, .rule-card small { color: var(--text-secondary); }
.inline-fields { display: flex; gap: 12px; }
</style>
