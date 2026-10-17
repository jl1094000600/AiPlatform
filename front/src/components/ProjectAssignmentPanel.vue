<template>
  <section class="assignment-panel">
    <div class="panel-head">
      <strong>项目指派与我的待办</strong>
      <el-button size="small" :loading="loading" @click="$emit('refresh')">刷新待办</el-button>
    </div>
    <el-tabs v-model="activeTab">
      <el-tab-pane :label="`我的待办 (${pendingMine.length})`" name="mine" />
      <el-tab-pane label="当前项目指派" name="project" :disabled="!projectKey" />
    </el-tabs>
    <p class="hint">{{ activeTab === 'mine' ? '跨可访问项目展示分配给你的待接受和处理中事项。完成指派不会自动通过审批或完成 Agent 执行。' : '查看当前项目的指派状态与处理人。接受后可开始处理，处理结束后再确认完成。' }}</p>
    <el-alert v-if="activeError" :title="activeError" type="error" :closable="false" show-icon />
    <el-table v-else :data="visibleAssignments" v-loading="loading" empty-text="暂无待办或指派" row-key="id">
      <el-table-column label="事项" min-width="170">
        <template #default="{ row }">
          <strong>{{ assignmentTargetText(row.targetType) }} #{{ row.targetId }}</strong>
          <div v-if="activeTab === 'mine'" class="project-name">{{ projectName(row.projectKey) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="处理人" min-width="110">
        <template #default="{ row }">{{ assigneeLabel(row.assigneeName, row.assigneeUserId) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><el-tag :type="row.status === 'COMPLETED' ? 'success' : row.status === 'ACCEPTED' ? 'primary' : 'info'">{{ assignmentStatusText(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="160">
        <template #default="{ row }">{{ formatProjectTime(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="215">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button v-if="row.canAccept" size="small" type="primary" :disabled="busy" @click="$emit('action', 'accept', row)">接受</el-button>
            <el-button v-if="row.canComplete" size="small" type="success" :disabled="busy" @click="$emit('action', 'complete', row)">完成</el-button>
            <el-button v-if="row.canReassign && row.projectKey === projectKey" size="small" :disabled="busy" @click="$emit('reassign', row)">改派</el-button>
            <el-button v-if="row.projectKey !== projectKey" size="small" text type="primary" @click="$emit('open-project', row.projectKey)">查看项目</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { activeMyAssignments, assigneeLabel, assignmentStatusText, assignmentTargetText, formatProjectTime } from '../utils/projectAssignments'

const props = defineProps({
  projectKey: { type: String, default: '' },
  assignments: { type: Array, default: () => [] },
  myAssignments: { type: Array, default: () => [] },
  workspaces: { type: Array, default: () => [] },
  loading: Boolean,
  busy: Boolean,
  assignmentsError: { type: String, default: '' },
  myAssignmentsError: { type: String, default: '' }
})
defineEmits(['refresh', 'action', 'reassign', 'open-project'])
const activeTab = ref('mine')
const pendingMine = computed(() => activeMyAssignments(props.myAssignments))
const visibleAssignments = computed(() => activeTab.value === 'mine' ? pendingMine.value : props.assignments)
const activeError = computed(() => activeTab.value === 'mine' ? props.myAssignmentsError : props.assignmentsError)
const projectName = (key) => props.workspaces.find(project => project.projectKey === key)?.projectName || key
watch(() => props.projectKey, key => { if (!key) activeTab.value = 'mine' })
</script>

<style scoped>
.assignment-panel { padding: 16px; border: 1px solid var(--glass-border); border-radius: 8px; background: var(--glass-bg); margin-bottom: 16px; }
.panel-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.hint, .project-name { color: var(--text-muted); font-size: 12px; line-height: 1.5; }
.hint { margin: 0 0 12px; }
.project-name { margin-top: 4px; }
.row-actions { display: flex; gap: 6px; flex-wrap: wrap; }
.row-actions :deep(.el-button + .el-button) { margin-left: 0; }
</style>
