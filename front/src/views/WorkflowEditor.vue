<template>
  <div class="page-container">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">工作流编排</h2>
        <span class="total-count mono">Workflow Orchestration</span>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="createNewWorkflow" class="create-btn">
          <Plus class="btn-icon" /> 新建编排
        </el-button>
      </div>
    </div>

    <!-- Workflow List -->
    <div class="workflow-list glass-card">
      <el-table
        :data="workflows"
        v-loading="loading"
        stripe
        @row-click="handleRowClick"
        class="workflow-table"
      >
        <el-table-column prop="id" label="ID" width="80" align="center">
          <template #default="{ row }">
            <span class="mono">#{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="workflowName" label="编排名称" min-width="160">
          <template #default="{ row }">
            <div class="workflow-name-cell">
              <span class="workflow-name">{{ row.workflowName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="180">
          <template #default="{ row }">
            <span class="description-text">{{ row.description || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="triggerType" label="触发类型" width="120" align="center">
          <template #default="{ row }">
            <span class="trigger-badge" :class="row.triggerType?.toLowerCase()">
              {{ getTriggerText(row.triggerType) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <span class="status-badge" :class="row.status === 1 ? 'enabled' : 'disabled'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="triggerCount" label="触发次数" width="100" align="center">
          <template #default="{ row }">
            <span class="mono">{{ row.triggerCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastTriggerTime" label="最后触发" min-width="160">
          <template #default="{ row }">
            <span class="mono">{{ formatTime(row.lastTriggerTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button size="small" type="primary" @click.stop="editWorkflow(row)">
                编辑
              </el-button>
              <el-button size="small" type="success" @click.stop="triggerWorkflow(row)">
                触发
              </el-button>
              <el-button size="small" @click.stop="viewExecutions(row)">
                历史
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Editor Dialog -->
    <el-dialog
      v-model="showEditor"
      :title="isEditing ? '编辑编排' : '新建编排'"
      width="90%"
      top="2vh"
      class="workflow-editor-dialog"
      :close-on-click-modal="false"
    >
      <div class="editor-container">
        <!-- Left: Node Palette -->
        <div class="node-palette glass-card">
          <h4 class="palette-title">节点组件</h4>
          <div class="palette-nodes">
            <div
              v-for="nodeType in nodeTypes"
              :key="nodeType.type"
              class="palette-node"
              draggable="true"
              @dragstart="onDragStart($event, nodeType)"
            >
              <span class="node-icon">{{ nodeType.icon }}</span>
              <span class="node-label">{{ nodeType.label }}</span>
            </div>
          </div>

          <h4 class="palette-title" style="margin-top: 20px;">属性配置</h4>
          <div v-if="selectedNode" class="node-properties">
            <div class="property-item">
              <label>节点ID</label>
              <el-input v-model="selectedNode.id" disabled size="small" />
            </div>
            <div class="property-item">
              <label>节点名称</label>
              <el-input v-model="selectedNode.label" size="small" />
            </div>
            <div class="property-item" v-if="selectedNode.type === 'AGENT'">
              <label>选择Agent</label>
              <el-select v-model="selectedNode.agentId" placeholder="请选择Agent" size="small">
                <el-option
                  v-for="agent in agents"
                  :key="agent.id"
                  :label="agent.agentName"
                  :value="agent.id"
                />
              </el-select>
            </div>
            <div class="property-item" v-if="selectedNode.type === 'AGENT'">
              <label>调用参数</label>
              <el-input
                v-model="selectedNode.params"
                type="textarea"
                :rows="3"
                size="small"
                placeholder="JSON格式参数"
              />
            </div>
            <div class="property-item" v-if="selectedNode.type === 'CONDITION'">
              <label>条件表达式</label>
              <el-input
                v-model="selectedNode.condition"
                type="textarea"
                :rows="2"
                size="small"
                placeholder="支持SpEL表达式"
              />
            </div>
            <div class="property-item" v-if="selectedNode.type === 'LOOP'">
              <label>循环次数</label>
              <el-input-number v-model="selectedNode.loopCount" :min="1" :max="100" size="small" />
            </div>
          </div>
          <div v-else class="no-selection">
            <p>点击节点进行配置</p>
          </div>
        </div>

        <!-- Center: Canvas -->
        <div class="workflow-canvas" ref="canvasRef">
          <div class="canvas-header">
            <div class="canvas-title">
              <el-input
                v-model="currentWorkflow.workflowName"
                placeholder="请输入编排名称"
                class="workflow-name-input"
              />
            </div>
            <div class="canvas-actions">
              <el-select v-model="currentWorkflow.triggerType" size="small" style="width: 120px;">
                <el-option label="手动触发" value="MANUAL" />
                <el-option label="定时触发" value="SCHEDULE" />
                <el-option label="事件触发" value="EVENT" />
              </el-select>
              <el-button size="small" @click="clearCanvas">清空画布</el-button>
              <el-button size="small" type="primary" @click="saveWorkflow">保存</el-button>
            </div>
          </div>

          <div class="canvas-content">
            <div class="workflow-nodes">
              <div
                v-for="node in workflowNodes"
                :key="node.id"
                class="workflow-node"
                :class="[node.type.toLowerCase(), { selected: selectedNode?.id === node.id }]"
                :style="{ left: node.x + 'px', top: node.y + 'px' }"
                @click.stop="selectNode(node)"
                @dblclick.stop="openNodeConfig(node)"
              >
                <span class="node-icon">{{ getNodeIcon(node.type) }}</span>
                <span class="node-name">{{ node.label }}</span>
                <span class="node-type-badge">{{ node.type }}</span>
              </div>
            </div>

            <svg class="edges-svg">
              <defs>
                <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
                  <polygon points="0 0, 10 3.5, 0 7" fill="#00f0ff" />
                </marker>
              </defs>
              <line
                v-for="(edge, index) in workflowEdges"
                :key="index"
                :x1="edge.x1"
                :y1="edge.y1"
                :x2="edge.x2"
                :y2="edge.y2"
                class="workflow-edge"
                marker-end="url(#arrowhead)"
                @click.stop="selectEdge(edge)"
              />
            </svg>
          </div>
        </div>

        <!-- Right: Properties Panel -->
        <div class="properties-panel glass-card">
          <h4 class="panel-title">编排属性</h4>
          <div class="workflow-properties">
            <div class="property-item">
              <label>编排编码</label>
              <el-input v-model="currentWorkflow.workflowCode" size="small" :disabled="isEditing" />
            </div>
            <div class="property-item">
              <label>描述</label>
              <el-input
                v-model="currentWorkflow.description"
                type="textarea"
                :rows="3"
                size="small"
              />
            </div>
            <div class="property-item" v-if="currentWorkflow.triggerType === 'SCHEDULE'">
              <label>Cron表达式</label>
              <el-input v-model="currentWorkflow.cron" size="small" placeholder="0 0 * * * ?" />
            </div>
            <div class="property-item" v-if="currentWorkflow.triggerType === 'EVENT'">
              <label>事件类型</label>
              <el-input v-model="currentWorkflow.eventType" size="small" placeholder="AgentStatusChanged" />
            </div>
          </div>

          <h4 class="panel-title" style="margin-top: 20px;">执行历史</h4>
          <div class="execution-list">
            <div v-if="executions.length === 0" class="no-data">暂无执行记录</div>
            <div v-for="exec in executions" :key="exec.id" class="execution-item">
              <div class="execution-header">
                <span class="execution-id mono">#{{ exec.id }}</span>
                <span class="execution-status" :class="exec.status?.toLowerCase()">{{ exec.status }}</span>
              </div>
              <div class="execution-time mono">
                {{ formatTime(exec.startTime) }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const canvasRef = ref(null)

// State
const workflows = ref([])
const loading = ref(false)
const showEditor = ref(false)
const isEditing = ref(false)
const currentWorkflow = ref({})
const workflowNodes = ref([])
const workflowEdges = ref([])
const selectedNode = ref(null)
const agents = ref([])
const executions = ref([])

// Node type definitions
const nodeTypes = [
  { type: 'START', label: '开始', icon: '\u25B6' },
  { type: 'AGENT', label: 'Agent节点', icon: '\u2699' },
  { type: 'CONDITION', label: '条件分支', icon: '\u2716' },
  { type: 'LOOP', label: '循环节点', icon: '\u21BB' },
  { type: 'PARALLEL', label: '并行执行', icon: '\u25CE' },
  { type: 'MERGE', label: '结果合并', icon: '\u2211' },
  { type: 'END', label: '结束', icon: '\u25A0' }
]

// Drag state
let draggedNodeType = null
let nodeIdCounter = 1

const getNodeIcon = (type) => {
  const node = nodeTypes.find(n => n.type === type)
  return node ? node.icon : '\u2B55'
}

const getTriggerText = (type) => {
  const map = { MANUAL: '手动', SCHEDULE: '定时', EVENT: '事件' }
  return map[type] || type
}

const formatTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  })
}

const loadWorkflows = async () => {
  loading.value = true
  try {
    const res = await api.getWorkflows()
    if (res.data.code === 200) {
      workflows.value = res.data.data || []
    }
  } catch (e) {
    console.error('加载编排列表失败', e)
  } finally {
    loading.value = false
  }
}

const loadAgents = async () => {
  try {
    const res = await api.getAgents()
    if (res.data.code === 200) {
      agents.value = res.data.data || []
    }
  } catch (e) {
    console.error('加载Agent列表失败', e)
  }
}

const loadExecutions = async (workflowId) => {
  try {
    const res = await api.getWorkflowExecutions(workflowId)
    if (res.data.code === 200) {
      executions.value = res.data.data || []
    }
  } catch (e) {
    console.error('加载执行历史失败', e)
  }
}

const createNewWorkflow = () => {
  isEditing.value = false
  currentWorkflow.value = {
    workflowName: '',
    description: '',
    triggerType: 'MANUAL',
    workflowDefinition: {}
  }
  workflowNodes.value = []
  workflowEdges.value = []
  nodeIdCounter = 1
  showEditor.value = true
}

const editWorkflow = async (row) => {
  isEditing.value = true
  try {
    const res = await api.getWorkflow(row.id)
    if (res.data.code === 200) {
      currentWorkflow.value = res.data.data || {}
      const definition = JSON.parse(currentWorkflow.value.workflowDefinition || '{}')
      workflowNodes.value = definition.nodes || []
      workflowEdges.value = definition.edges || []
      if (workflowNodes.value.length > 0) {
        const maxId = Math.max(...workflowNodes.value.map(n => parseInt(n.id) || 0))
        nodeIdCounter = maxId + 1
      }
      await loadExecutions(row.id)
      showEditor.value = true
    }
  } catch (e) {
    console.error('加载编排详情失败', e)
    ElMessage.error('加载编排详情失败')
  }
}

const handleRowClick = (row) => {
  editWorkflow(row)
}

const triggerWorkflow = async (row) => {
  try {
    await ElMessageBox.confirm('确定要触发该编排吗？', '触发确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await api.triggerWorkflow(row.id)
    if (res.data.code === 200) {
      ElMessage.success('编排触发成功')
      loadWorkflows()
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('触发失败')
    }
  }
}

const viewExecutions = async (row) => {
  await loadExecutions(row.id)
  currentWorkflow.value = { id: row.id, workflowName: row.workflowName }
  showEditor.value = true
}

// Drag and Drop
const onDragStart = (event, nodeType) => {
  draggedNodeType = nodeType
}

const onCanvasDrop = async (event) => {
  event.preventDefault()
  if (!draggedNodeType || !canvasRef.value) return

  const rect = canvasRef.value.getBoundingClientRect()
  const x = event.clientX - rect.left
  const y = event.clientY - rect.top

  const newNode = {
    id: String(nodeIdCounter++),
    type: draggedNodeType.type,
    label: draggedNodeType.label,
    x: x - 50,
    y: y - 20,
    agentId: null,
    params: '{}',
    condition: '',
    loopCount: 1
  }

  workflowNodes.value.push(newNode)
  draggedNodeType = null
}

const selectNode = (node) => {
  selectedNode.value = node
}

const openNodeConfig = (node) => {
  selectedNode.value = node
}

const selectEdge = (edge) => {
  // Edge selection logic
}

const clearCanvas = () => {
  workflowNodes.value = []
  workflowEdges.value = []
  selectedNode.value = null
}

const saveWorkflow = async () => {
  if (!currentWorkflow.value.workflowName) {
    ElMessage.warning('请输入编排名称')
    return
  }

  const definition = {
    nodes: workflowNodes.value,
    edges: workflowEdges.value
  }

  const data = {
    ...currentWorkflow.value,
    workflowDefinition: JSON.stringify(definition)
  }

  try {
    if (isEditing.value) {
      await api.updateWorkflow(currentWorkflow.value.id, data)
    } else {
      await api.createWorkflow(data)
    }
    ElMessage.success('保存成功')
    showEditor.value = false
    loadWorkflows()
  } catch (e) {
    console.error('保存编排失败', e)
    ElMessage.error('保存失败')
  }
}

onMounted(() => {
  loadWorkflows()
  loadAgents()

  // Setup canvas drop zone
  if (canvasRef.value) {
    canvasRef.value.addEventListener('drop', onCanvasDrop)
    canvasRef.value.addEventListener('dragover', (e) => e.preventDefault())
  }
})
</script>

<style scoped>
.page-container {
  animation: fadeInUp 0.4s ease;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: baseline;
  gap: 16px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
}

.total-count {
  font-size: 13px;
  color: var(--text-muted);
}

.header-actions {
  display: flex;
  gap: 12px;
}

.create-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: linear-gradient(135deg, var(--accent-cyan), var(--accent-purple));
  border: none;
}

.btn-icon {
  width: 14px;
  height: 14px;
}

/* Workflow List */
.workflow-list {
  padding: 20px;
}

.workflow-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(26, 31, 78, 0.5);
}

.workflow-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workflow-name {
  font-weight: 500;
}

.description-text {
  color: var(--text-muted);
  font-size: 13px;
}

.trigger-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
}

.trigger-badge.manual {
  background: rgba(0, 240, 255, 0.15);
  color: var(--accent-cyan);
}

.trigger-badge.schedule {
  background: rgba(245, 158, 11, 0.15);
  color: var(--accent-amber);
}

.trigger-badge.event {
  background: rgba(124, 58, 237, 0.15);
  color: var(--accent-purple);
}

.status-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
}

.status-badge.enabled {
  background: rgba(16, 185, 129, 0.15);
  color: var(--accent-green);
}

.status-badge.disabled {
  background: rgba(156, 163, 175, 0.15);
  color: #9ca3af;
}

.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
}

/* Editor Dialog */
.editor-container {
  display: flex;
  gap: 16px;
  height: 75vh;
}

/* Node Palette */
.node-palette {
  width: 180px;
  padding: 16px;
  overflow-y: auto;
}

.palette-title {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 12px;
}

.palette-nodes {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.palette-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: rgba(26, 31, 78, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  cursor: grab;
  transition: all 0.2s;
}

.palette-node:hover {
  background: rgba(0, 240, 255, 0.1);
  border-color: var(--accent-cyan);
}

.palette-node:active {
  cursor: grabbing;
}

.node-icon {
  font-size: 16px;
}

.node-label {
  font-size: 12px;
}

/* Node Properties */
.node-properties {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.property-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.property-item label {
  font-size: 11px;
  color: var(--text-muted);
}

.no-selection {
  color: var(--text-muted);
  font-size: 12px;
  text-align: center;
  padding: 20px 0;
}

/* Workflow Canvas */
.workflow-canvas {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: rgba(10, 14, 39, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  overflow: hidden;
}

.canvas-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: rgba(26, 31, 78, 0.6);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.workflow-name-input {
  width: 200px;
}

.canvas-actions {
  display: flex;
  gap: 8px;
}

.canvas-content {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.workflow-nodes {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.workflow-node {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(26, 31, 78, 0.9);
  border: 2px solid rgba(255, 255, 255, 0.2);
  border-radius: 10px;
  cursor: move;
  transition: all 0.2s;
  min-width: 120px;
}

.workflow-node:hover {
  border-color: var(--accent-cyan);
  box-shadow: 0 0 20px rgba(0, 240, 255, 0.3);
}

.workflow-node.selected {
  border-color: var(--accent-cyan);
  box-shadow: 0 0 20px rgba(0, 240, 255, 0.5);
}

.workflow-node.start,
.workflow-node.end {
  border-radius: 50%;
  padding: 12px 20px;
}

.workflow-node.start {
  border-color: var(--accent-green);
}

.workflow-node.end {
  border-color: var(--neon-pink);
}

.workflow-node.agent {
  border-color: var(--accent-cyan);
}

.workflow-node.condition {
  border-color: var(--accent-amber);
}

.workflow-node.loop {
  border-color: var(--accent-purple);
}

.workflow-node .node-icon {
  font-size: 14px;
}

.workflow-node .node-name {
  font-size: 12px;
  font-weight: 500;
}

.workflow-node .node-type-badge {
  font-size: 10px;
  padding: 2px 6px;
  background: rgba(0, 0, 0, 0.3);
  border-radius: 4px;
  color: var(--text-muted);
}

/* Edges SVG */
.edges-svg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.workflow-edge {
  stroke: var(--accent-cyan);
  stroke-width: 2;
  stroke-opacity: 0.6;
  pointer-events: stroke;
}

.workflow-edge:hover {
  stroke-opacity: 1;
  stroke-width: 3;
}

/* Properties Panel */
.properties-panel {
  width: 220px;
  padding: 16px;
  overflow-y: auto;
}

.panel-title {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 12px;
}

.workflow-properties {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* Execution List */
.execution-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
}

.no-data {
  color: var(--text-muted);
  font-size: 12px;
  text-align: center;
  padding: 20px 0;
}

.execution-item {
  padding: 10px;
  background: rgba(26, 31, 78, 0.6);
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.execution-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.execution-id {
  font-size: 12px;
  color: var(--text-primary);
}

.execution-status {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
}

.execution-status.pending {
  background: rgba(245, 158, 11, 0.15);
  color: var(--accent-amber);
}

.execution-status.running {
  background: rgba(0, 240, 255, 0.15);
  color: var(--accent-cyan);
}

.execution-status.completed {
  background: rgba(16, 185, 129, 0.15);
  color: var(--accent-green);
}

.execution-status.failed {
  background: rgba(239, 68, 68, 0.15);
  color: var(--accent-red);
}

.execution-time {
  font-size: 11px;
  color: var(--text-muted);
}
</style>
