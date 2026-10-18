<template>
  <div class="page-container">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">工作流编排</h2>
        <span class="total-count mono">Workflow Orchestration</span>
      </div>
      <div class="header-actions">
        <el-button v-if="canManageWorkflow" type="primary" @click="createNewWorkflow" class="create-btn">
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
              <el-button v-if="canManageWorkflow" size="small" type="primary" @click.stop="editWorkflow(row)">
                编辑
              </el-button>
              <el-button v-if="canManageWorkflow" size="small" type="success" @click.stop="triggerWorkflow(row)">
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
      <el-alert v-if="workflowSupportMessage" :title="workflowSupportMessage" type="warning" :closable="false" show-icon class="workflow-support-warning" />
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
                v-model="selectedNode.paramsText"
                type="textarea"
                :rows="3"
                size="small"
                placeholder="JSON格式参数"
              />
            </div>
            <div class="property-item" v-if="selectedNode.type === 'CONDITION'">
              <label>条件字段</label>
              <el-input v-model="selectedNode.conditionField" size="small" placeholder="例如 status" />
            </div>
            <div class="property-item" v-if="selectedNode.type === 'CONDITION'">
              <label>比较方式</label>
              <el-select v-model="selectedNode.operator" size="small">
                <el-option v-for="operator in conditionOperators" :key="operator" :label="operator" :value="operator" />
              </el-select>
            </div>
            <div class="property-item" v-if="selectedNode.type === 'CONDITION' && selectedNode.operator !== 'EXISTS'">
              <label>比较值</label>
              <el-input v-model="selectedNode.conditionValue" size="small" />
            </div>
            <div class="property-item" v-if="selectedNode.type === 'AGENT'">
              <label>超时 / 重试</label>
              <div class="inline-controls">
                <el-input-number v-model="selectedNode.timeout" :min="1" :max="300" size="small" />
                <el-input-number v-model="selectedNode.retryCount" :min="0" :max="5" size="small" />
              </div>
            </div>
            <div class="property-item">
              <label>输入绑定</label>
              <div class="binding-list">
                <div v-for="(binding, index) in selectedNode.inputBindings" :key="index" class="binding-row">
                  <el-input v-model="binding.key" placeholder="key" size="small" class="binding-key" />
                  <el-input
                    v-model="binding.value"
                    type="textarea"
                    :rows="2"
                    size="small"
                    class="binding-value"
                    placeholder="字面量或 {{variables.x}} / {{nodes.n.outputs.y}} / {{inputs.z}}"
                  />
                  <div class="binding-row-actions">
                    <el-dropdown trigger="click" @command="(cmd) => insertRefIntoBinding(binding, cmd)">
                      <el-button size="small" text type="primary">插入引用</el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item
                            v-for="item in referenceSuggestions"
                            :key="item.reference"
                            :command="item.reference"
                          >
                            {{ item.label }}
                          </el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                    <el-button size="small" text type="danger" @click="removeBinding(index)">移除</el-button>
                  </div>
                </div>
              </div>
              <el-button size="small" @click="addBinding">添加输入</el-button>
              <div v-if="selectedNodeBindingErrors.length" class="binding-errors">
                <div v-for="(err, i) in selectedNodeBindingErrors" :key="i" class="binding-error">{{ err }}</div>
              </div>
            </div>
            <div class="node-actions">
              <el-button size="small" @click="beginConnection(selectedNode)">
                {{ connectionSourceId === selectedNode.id ? '已选为起点' : '设为连线起点' }}
              </el-button>
              <el-button v-if="connectionSourceId && connectionSourceId !== selectedNode.id" size="small" type="primary" @click="connectToNode(selectedNode)">连接到此节点</el-button>
              <el-button size="small" type="danger" @click="deleteNode(selectedNode)">删除节点</el-button>
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
              <el-tag v-if="viewingVersion" type="warning" size="small" class="version-tag">
                正在查看版本 #{{ viewingVersion.versionNo }}
                <template v-if="editorLocked"> · 只读</template>
              </el-tag>
            </div>
            <div class="canvas-actions">
              <el-select v-model="currentWorkflow.triggerType" size="small" style="width: 120px;">
                <el-option label="手动触发" value="MANUAL" />
                <el-option label="定时触发" value="SCHEDULE" />
                <el-option label="事件触发" value="EVENT" />
              </el-select>
              <el-button v-if="viewingVersion && editorLocked" size="small" type="primary" @click="unlockEditing">编辑</el-button>
              <el-button v-if="viewingVersion" size="small" @click="returnToDraft">返回草稿</el-button>
              <el-dropdown trigger="click" @command="onVersionCommand">
                <el-button size="small" :disabled="!currentWorkflow.id">
                  版本
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="workflowVersions.length === 0" disabled>暂无已发布版本</el-dropdown-item>
                    <el-dropdown-item
                      v-for="version in workflowVersions"
                      :key="version.id"
                      :command="{ action: 'detail', version }"
                    >
                      #{{ version.versionNo }} · {{ version.status }} · {{ formatTime(version.publishTime) }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button
                size="small"
                type="warning"
                :disabled="!canManageWorkflow || !currentWorkflow.id || !!workflowSupportMessage"
                @click="publishVersion"
              >
                发布版本
              </el-button>
              <el-button
                size="small"
                type="success"
                :disabled="!canManageWorkflow || !currentWorkflow.id"
                @click="deployCurrentDraft"
              >
                冻结当前草稿为新版本
              </el-button>
              <el-button size="small" :disabled="!canvasEditable" @click="clearCanvas">清空画布</el-button>
              <el-button size="small" type="primary" :disabled="!canvasEditable" @click="saveWorkflow">保存</el-button>
            </div>
          </div>

          <div class="canvas-content">
            <WorkflowCanvas
              :nodes="workflowNodes"
              :edges="workflowEdges"
              @update:nodes="onCanvasNodesUpdate"
              @update:edges="onCanvasEdgesUpdate"
              @select-node="selectNode"
              @select-edge="selectEdge"
              @clear-selection="clearSelection"
              @viewport-change="onViewportChange"
            />
            <el-button v-if="selectedEdge" class="delete-edge-btn" size="small" type="danger" @click="deleteSelectedEdge">删除连线</el-button>
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

          <h4 class="panel-title" style="margin-top: 20px;">工作流变量</h4>
          <div class="variables-panel">
            <div v-if="workflowVariables.length === 0" class="no-data">暂无变量声明</div>
            <div v-for="(variable, index) in workflowVariables" :key="index" class="variable-row">
              <el-input v-model="variable.name" placeholder="name" size="small" />
              <div class="variable-row-inline">
                <el-select v-model="variable.type" size="small" class="variable-type">
                  <el-option v-for="type in variableTypes" :key="type" :label="type" :value="type" />
                </el-select>
                <el-checkbox v-model="variable.required">必填</el-checkbox>
              </div>
              <el-input v-model="variable.defaultValueText" placeholder="defaultValue（可空）" size="small" />
              <el-input v-model="variable.description" placeholder="描述（可空）" size="small" />
              <el-button size="small" text type="danger" @click="removeVariable(index)">删除变量</el-button>
            </div>
            <el-button size="small" @click="addVariable">添加变量</el-button>
            <div v-if="variableErrors.length" class="binding-errors">
              <div v-for="(err, i) in variableErrors" :key="i" class="binding-error">{{ err }}</div>
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

    <!-- Version Detail Dialog -->
    <el-dialog
      v-model="showVersionDetail"
      :title="activeVersion ? `版本详情 #${activeVersion.versionNo}` : '版本详情'"
      width="60%"
      class="version-detail-dialog"
      :close-on-click-modal="false"
    >
      <div v-if="activeVersion" class="version-detail">
        <div class="version-meta">
          <span class="mono">版本ID: {{ activeVersion.id }}</span>
          <span class="mono">状态: {{ activeVersion.status }}</span>
          <span class="mono">发布于: {{ formatTime(activeVersion.publishTime) }}</span>
          <span v-if="activeVersion.definitionHash" class="mono version-hash">Hash: {{ activeVersion.definitionHash }}</span>
        </div>
        <el-tabs v-model="versionDetailTab">
          <el-tab-pane label="Definition" name="definition">
            <pre class="json-block">{{ formatJson(activeVersion.definitionJson) }}</pre>
          </el-tab-pane>
          <el-tab-pane label="Layout" name="layout">
            <pre class="json-block">{{ formatJson(activeVersion.layoutJson) }}</pre>
          </el-tab-pane>
        </el-tabs>
        <div class="version-detail-actions">
          <el-button
            type="primary"
            :disabled="!canManageWorkflow"
            @click="switchToVersion(activeVersion)"
          >
            切换查看（回填画布）
          </el-button>
          <el-button @click="showVersionDetail = false">关闭</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, nextTick } from 'vue'
import { Plus, ArrowDown } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { hasPermission } from '../utils/permissions'
import WorkflowCanvas from '../components/WorkflowCanvas.vue'
import {
  WORKFLOW_NODE_TYPES,
  VARIABLE_TYPES,
  workflowSupportError,
  buildWorkflowDefinition,
  buildWorkflowLayout,
  applyWorkflowLayout,
  extractLayoutFromNodes,
  parseWorkflowDefinition,
  inputsToBindings,
  bindingsToInputs,
  normalizeVariables,
  variablesToEditable,
  validateTemplateReferences,
  insertTemplateReference,
  buildReferenceSuggestions
} from '../utils/workflowCanvas.js'

const canvasRef = ref(null)

// State
const workflows = ref([])
const loading = ref(false)
const showEditor = ref(false)
const isEditing = ref(false)
const currentWorkflow = ref({})
const workflowNodes = ref([])
const workflowEdges = ref([])
const workflowVariables = ref([])
const workflowLayout = ref({ nodes: [] })
const canvasViewport = ref({ x: 0, y: 0, zoom: 1 })
const selectedNode = ref(null)
const selectedEdge = ref(null)
const connectionSourceId = ref(null)
const agents = ref([])
const executions = ref([])
const workflowVersions = ref([])
const viewingVersion = ref(null)
const editorLocked = ref(false)
const canvasEditable = computed(() => !editorLocked.value && canManageWorkflow.value && !workflowSupportMessage.value)
const showVersionDetail = ref(false)
const activeVersion = ref(null)
const versionDetailTab = ref('definition')
const canManageWorkflow = computed(() => hasPermission('workflow:manage'))
const workflowSupportMessage = computed(() => workflowSupportError({ nodes: workflowNodes.value }))
const variableTypes = VARIABLE_TYPES

const variableErrors = computed(() => {
  const errors = []
  const seen = new Set()
  workflowVariables.value.forEach((variable, index) => {
    const name = (variable.name || '').trim()
    if (!name) {
      errors.push(`第 ${index + 1} 个变量缺少名称`)
      return
    }
    if (seen.has(name)) errors.push(`变量名「${name}」重复`)
    seen.add(name)
  })
  return errors
})

const selectedNodeBindingErrors = computed(() => {
  if (!selectedNode.value) return []
  return validateTemplateReferences(bindingsToInputs(selectedNode.value.inputBindings), {
    variableNames: workflowVariables.value.map(item => item.name).filter(Boolean),
    nodeIds: workflowNodes.value.map(item => item.id)
  })
})

const referenceSuggestions = computed(() => buildReferenceSuggestions({
  variables: workflowVariables.value.map(item => item.name).filter(Boolean),
  nodes: workflowNodes.value,
  currentNodeId: selectedNode.value?.id,
  edges: workflowEdges.value
}))

// Node type definitions
const nodeTypes = WORKFLOW_NODE_TYPES
const conditionOperators = ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE', 'CONTAINS', 'EXISTS']

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
      agents.value = res.data.data?.records || res.data.data || []
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
  if (!canManageWorkflow.value) {
    ElMessage.warning('当前账号无工作流管理权限')
    return
  }
  isEditing.value = false
  currentWorkflow.value = {
    workflowName: '',
    workflowCode: `WF_${Date.now()}`,
    description: '',
    triggerType: 'MANUAL',
    workflowDefinition: {}
  }
  workflowNodes.value = []
  workflowEdges.value = []
  workflowVariables.value = []
  workflowLayout.value = { nodes: [] }
  canvasViewport.value = { x: 0, y: 0, zoom: 1 }
  viewingVersion.value = null
  workflowVersions.value = []
  nodeIdCounter = 1
  showEditor.value = true
}

const layoutStorageKey = (workflow) => {
  if (workflow?.id) return `ai-platform:workflow-layout:${workflow.id}`
  return `ai-platform:workflow-layout:draft:${workflow?.workflowCode || 'new'}`
}

const readStoredLayout = (workflow) => {
  if (workflow?.layoutJson) {
    try {
      return typeof workflow.layoutJson === 'string' ? JSON.parse(workflow.layoutJson) : workflow.layoutJson
    } catch (_) {
      /* fall through to localStorage */
    }
  }
  try {
    if (typeof localStorage === 'undefined') return null
    const raw = localStorage.getItem(layoutStorageKey(workflow))
    return raw ? JSON.parse(raw) : null
  } catch (_) {
    return null
  }
}

const writeStoredLayout = (workflow, layout) => {
  try {
    if (typeof localStorage === 'undefined') return
    localStorage.setItem(layoutStorageKey(workflow), JSON.stringify(layout))
  } catch (_) {
    /* storage unavailable — layout still ships with published versions */
  }
}

const editWorkflow = async (row) => {
  if (!canManageWorkflow.value) {
    ElMessage.warning('当前账号无工作流管理权限')
    return
  }
  isEditing.value = true
  viewingVersion.value = null
  try {
    const res = await api.getWorkflow(row.id)
    if (res.data.code === 200) {
      currentWorkflow.value = res.data.data || {}
      try {
        const triggerConfig = JSON.parse(currentWorkflow.value.triggerConfig || '{}')
        currentWorkflow.value.cron = triggerConfig.cron || ''
        currentWorkflow.value.eventType = triggerConfig.eventType || ''
      } catch (_) {
        currentWorkflow.value.cron = ''
        currentWorkflow.value.eventType = ''
      }
      const storedLayout = readStoredLayout(currentWorkflow.value)
      hydrateCanvasFromDefinition(currentWorkflow.value.workflowDefinition, storedLayout)
      if (workflowNodes.value.length > 0) {
        const maxId = Math.max(...workflowNodes.value.map(n => parseInt(n.id) || 0))
        nodeIdCounter = maxId + 1
      }
      loadWorkflowVersions(row.id)
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
  if (!canManageWorkflow.value) {
    ElMessage.warning('当前账号无工作流管理权限')
    return
  }
  try {
    const detail = await api.getWorkflow(row.id)
    if (detail.data.code !== 200 || !detail.data.data) throw new Error('加载编排详情失败')
    const workflow = detail.data.data
    const supportError = workflowSupportError(workflow.workflowDefinition)
    if (supportError) {
      ElMessage.warning(supportError)
      return
    }
    await ElMessageBox.confirm('确定要触发该编排吗？', '触发确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    if (workflow.status !== 1) await api.deployWorkflow(row.id)
    const res = await api.triggerWorkflow(row.id, {}, 'MANUAL')
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
  const payload = {
    id: String(nodeIdCounter++),
    type: nodeType.type,
    label: nodeType.label
  }
  event.dataTransfer.setData('application/workflow-node', JSON.stringify(payload))
  event.dataTransfer.effectAllowed = 'move'
}

const onCanvasNodesUpdate = (nodes) => {
  workflowNodes.value = nodes.map(node => ({
    ...node,
    paramsText: node.paramsText ?? JSON.stringify(node.params || {}, null, 2),
    inputBindings: node.inputBindings ?? inputsToBindings(node.inputs),
    conditionField: node.conditionField ?? node.condition?.field ?? '',
    operator: node.operator || node.condition?.operator || 'EQ',
    conditionValue: node.conditionValue ?? node.condition?.value ?? ''
  }))
  if (selectedNode.value) {
    selectedNode.value = workflowNodes.value.find(item => item.id === selectedNode.value.id) || null
  }
}

const onCanvasEdgesUpdate = (edges) => {
  workflowEdges.value = edges
  if (selectedEdge.value) {
    selectedEdge.value = workflowEdges.value.find(item =>
      item.source === selectedEdge.value.source && item.target === selectedEdge.value.target) || null
  }
}

const clearSelection = () => {
  selectedNode.value = null
  selectedEdge.value = null
  connectionSourceId.value = null
}

const selectNode = (node) => {
  selectedNode.value = node
}

const openNodeConfig = (node) => {
  selectedNode.value = node
}

const selectEdge = (edge) => {
  selectedEdge.value = edge
  selectedNode.value = null
}

const beginConnection = (node) => {
  connectionSourceId.value = node.id
}

const connectToNode = (node) => {
  if (!connectionSourceId.value || connectionSourceId.value === node.id) return
  const duplicate = workflowEdges.value.some(edge => edge.source === connectionSourceId.value && edge.target === node.id)
  if (!duplicate) workflowEdges.value.push({ source: connectionSourceId.value, target: node.id })
  connectionSourceId.value = null
}

const deleteNode = (node) => {
  workflowNodes.value = workflowNodes.value.filter(item => item.id !== node.id)
  workflowEdges.value = workflowEdges.value.filter(edge => edge.source !== node.id && edge.target !== node.id)
  selectedNode.value = null
  if (connectionSourceId.value === node.id) connectionSourceId.value = null
}

const deleteSelectedEdge = () => {
  workflowEdges.value = workflowEdges.value.filter(edge => edge !== selectedEdge.value)
  selectedEdge.value = null
}

const edgeCoordinates = (edge) => {
  const source = workflowNodes.value.find(node => node.id === edge.source)
  const target = workflowNodes.value.find(node => node.id === edge.target)
  return {
    x1: (source?.x || 0) + 60,
    y1: (source?.y || 0) + 30,
    x2: (target?.x || 0) + 60,
    y2: (target?.y || 0) + 30
  }
}

const clearCanvas = () => {
  workflowNodes.value = []
  workflowEdges.value = []
  selectedNode.value = null
  selectedEdge.value = null
  connectionSourceId.value = null
}

const collectReferenceErrors = () => {
  const variableNames = workflowVariables.value.map(item => (item.name || '').trim()).filter(Boolean)
  const nodeIds = workflowNodes.value.map(item => String(item.id))
  const errors = [...variableErrors.value]
  for (const node of workflowNodes.value) {
    const refErrors = validateTemplateReferences(bindingsToInputs(node.inputBindings), { variableNames, nodeIds })
    for (const err of refErrors) errors.push(`节点 ${node.id}: ${err}`)
  }
  return errors
}

const normalizeNodesForDefinition = () => {
  return workflowNodes.value.map(node => {
    const normalized = {
      ...node,
      id: String(node.id),
      inputs: bindingsToInputs(node.inputBindings)
    }
    delete normalized.paramsText
    delete normalized.inputBindings
    delete normalized.x
    delete normalized.y
    if (node.type === 'AGENT') normalized.params = JSON.parse(node.paramsText || '{}')
    if (node.type === 'CONDITION') {
      normalized.condition = {
        field: node.conditionField,
        operator: node.operator,
        value: node.conditionValue
      }
    }
    return normalized
  })
}

const saveWorkflow = async () => {
  if (!canManageWorkflow.value) {
    ElMessage.warning('当前账号无工作流管理权限')
    return
  }
  if (workflowSupportMessage.value) {
    ElMessage.warning(workflowSupportMessage.value)
    return
  }
  if (!currentWorkflow.value.workflowName) {
    ElMessage.warning('请输入编排名称')
    return
  }
  if (!currentWorkflow.value.workflowCode) {
    ElMessage.warning('请输入编排编码')
    return
  }
  const refErrors = collectReferenceErrors()
  if (refErrors.length) {
    ElMessage.warning(refErrors[0])
    return
  }

  let normalizedNodes
  try {
    normalizedNodes = normalizeNodesForDefinition()
  } catch (error) {
    ElMessage.error('Agent调用参数必须是合法JSON')
    return
  }

  const definition = buildWorkflowDefinition({
    nodes: normalizedNodes,
    edges: workflowEdges.value.map(edge => ({ source: edge.source, target: edge.target, condition: edge.condition })),
    variables: workflowVariables.value
  })

  const layout = buildWorkflowLayout(workflowNodes.value, canvasViewport.value)
  workflowLayout.value = layout

  const triggerConfig = currentWorkflow.value.triggerType === 'SCHEDULE'
    ? JSON.stringify({ cron: currentWorkflow.value.cron })
    : currentWorkflow.value.triggerType === 'EVENT'
      ? JSON.stringify({ eventType: currentWorkflow.value.eventType })
      : null

  const data = {
    ...currentWorkflow.value,
    triggerConfig,
    workflowDefinition: JSON.stringify(definition),
    layoutJson: JSON.stringify(layout)
  }

  // Viewing a version and unlocked for editing: ask overwrite vs new version.
  if (viewingVersion.value && viewingVersion.value.id) {
    try {
      const choice = await ElMessageBox.confirm(
        `当前在编辑版本 #${viewingVersion.value.versionNo}。保存时选择策略：`,
        '保存策略',
        {
          distinguishCancelAndClose: true,
          confirmButtonText: '另存新版本',
          cancelButtonText: '覆盖原版本',
          type: 'warning'
        }
      ).then(() => 'new').catch((action) => {
        if (action === 'cancel') return 'overwrite'
        return null
      })
      if (!choice) return
      const versionPayload = {
        definitionJson: JSON.stringify(definition),
        layoutJson: JSON.stringify(layout)
      }
      if (choice === 'overwrite') {
        await api.overwriteWorkflowVersion(currentWorkflow.value.id, viewingVersion.value.id, versionPayload)
        ElMessage.success(`已覆盖版本 #${viewingVersion.value.versionNo}`)
      } else {
        await api.publishWorkflowVersion(currentWorkflow.value.id, versionPayload)
        ElMessage.success('已另存为新版本')
      }
      // Draft also keeps the latest content + layout so restarts recover it.
      await api.updateWorkflow(currentWorkflow.value.id, data)
      writeStoredLayout(currentWorkflow.value, layout)
      await loadWorkflowVersions(currentWorkflow.value.id)
      editorLocked.value = true
      return
    } catch (e) {
      if (e !== 'cancel' && e !== 'close') {
        console.error('保存版本失败', e)
        ElMessage.error('保存失败')
      }
      return
    }
  }

  try {
    if (isEditing.value) {
      await api.updateWorkflow(currentWorkflow.value.id, data)
    } else {
      const created = await api.createWorkflow(data)
      const payload = created?.data?.data
      const newId = payload && typeof payload === 'object' ? payload.id : payload
      if (newId !== undefined && newId !== null) currentWorkflow.value.id = newId
    }
    writeStoredLayout(currentWorkflow.value, layout)
    ElMessage.success('保存成功')
    showEditor.value = false
    loadWorkflows()
  } catch (e) {
    console.error('保存编排失败', e)
    ElMessage.error('保存失败')
  }
}

const addVariable = () => {
  workflowVariables.value.push({ name: '', type: 'string', required: false, defaultValueText: '', description: '' })
}

const removeVariable = (index) => {
  workflowVariables.value.splice(index, 1)
}

const addBinding = () => {
  if (!selectedNode.value) return
  if (!Array.isArray(selectedNode.value.inputBindings)) selectedNode.value.inputBindings = []
  selectedNode.value.inputBindings.push({ key: '', value: '' })
}

const removeBinding = (index) => {
  if (!selectedNode.value?.inputBindings) return
  selectedNode.value.inputBindings.splice(index, 1)
}

const insertRefIntoBinding = (binding, reference) => {
  if (!binding || !reference) return
  binding.value = insertTemplateReference(binding.value, reference)
}

const onViewportChange = (viewport) => {
  if (viewport && Number.isFinite(viewport.x) && Number.isFinite(viewport.y) && Number.isFinite(viewport.zoom)) {
    canvasViewport.value = { x: viewport.x, y: viewport.y, zoom: viewport.zoom }
  }
}

const formatJson = (value) => {
  if (value === null || value === undefined || value === '') return '(空)'
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return JSON.stringify(parsed, null, 2)
  } catch (_) {
    return String(value)
  }
}

const loadWorkflowVersions = async (workflowId) => {
  if (!workflowId) return
  try {
    const res = await api.getWorkflowVersions(workflowId)
    if (res.data.code === 200) {
      workflowVersions.value = res.data.data || []
    }
  } catch (e) {
    console.error('加载版本列表失败', e)
  }
}

const publishVersion = async () => {
  if (!canManageWorkflow.value) {
    ElMessage.warning('当前账号无工作流管理权限')
    return
  }
  if (!currentWorkflow.value.id) {
    ElMessage.warning('请先保存编排再发布版本')
    return
  }
  if (workflowSupportMessage.value) {
    ElMessage.warning(workflowSupportMessage.value)
    return
  }
  const refErrors = collectReferenceErrors()
  if (refErrors.length) {
    ElMessage.warning(refErrors[0])
    return
  }
  let normalizedNodes
  try {
    normalizedNodes = normalizeNodesForDefinition()
  } catch (error) {
    ElMessage.error('Agent调用参数必须是合法JSON')
    return
  }
  const definition = buildWorkflowDefinition({
    nodes: normalizedNodes,
    edges: workflowEdges.value.map(edge => ({ source: edge.source, target: edge.target, condition: edge.condition })),
    variables: workflowVariables.value
  })
  const layout = buildWorkflowLayout(workflowNodes.value, canvasViewport.value)
  workflowLayout.value = layout
  try {
    const res = await api.publishWorkflowVersion(currentWorkflow.value.id, {
      definitionJson: JSON.stringify(definition),
      layoutJson: JSON.stringify(layout)
    })
    if (res.data.code === 200) {
      ElMessage.success('版本发布成功')
      await loadWorkflowVersions(currentWorkflow.value.id)
    }
  } catch (e) {
    console.error('发布版本失败', e)
    ElMessage.error('发布版本失败')
  }
}

const deployCurrentDraft = async () => {
  if (!canManageWorkflow.value) {
    ElMessage.warning('当前账号无工作流管理权限')
    return
  }
  if (!currentWorkflow.value.id) {
    ElMessage.warning('请先保存编排')
    return
  }
  try {
    await ElMessageBox.confirm('将当前草稿冻结为新版本并部署，确定继续？', '冻结确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    const res = await api.deployWorkflow(currentWorkflow.value.id)
    if (res.data.code === 200) {
      ElMessage.success('已冻结当前草稿为新版本')
      await loadWorkflowVersions(currentWorkflow.value.id)
    }
  } catch (e) {
    console.error('部署失败', e)
    ElMessage.error('部署失败')
  }
}

const onVersionCommand = async (command) => {
  if (!command?.version) return
  if (command.action === 'detail') {
    try {
      const res = await api.getWorkflowVersion(currentWorkflow.value.id, command.version.id)
      if (res.data.code === 200) {
        activeVersion.value = res.data.data || command.version
        versionDetailTab.value = 'definition'
        showVersionDetail.value = true
      }
    } catch (e) {
      console.error('加载版本详情失败', e)
      ElMessage.error('加载版本详情失败')
    }
    return
  }
  if (command.action === 'switch') {
    await switchToVersion(command.version)
  }
}

const hydrateCanvasFromDefinition = (definitionJson, layout) => {
  const parsed = parseWorkflowDefinition(definitionJson)
  workflowVariables.value = variablesToEditable(parsed.variables)
  workflowEdges.value = (parsed.edges || []).map(edge => ({ ...edge }))
  let nodes = parsed.nodes.map(node => ({
    ...node,
    paramsText: JSON.stringify(node.params || {}, null, 2),
    inputBindings: inputsToBindings(node.inputs),
    conditionField: node.condition && typeof node.condition === 'object' ? node.condition.field : (node.conditionField ?? ''),
    operator: node.condition && typeof node.condition === 'object' ? node.condition.operator : (node.condition?.operator || node.operator || 'EQ'),
    conditionValue: node.condition && typeof node.condition === 'object' ? node.condition.value : (node.condition?.value ?? node.conditionValue ?? '')
  }))
  const resolvedLayout = layout || extractLayoutFromNodes(nodes)
  nodes = applyWorkflowLayout(nodes, resolvedLayout)
  workflowNodes.value = nodes
  workflowLayout.value = resolvedLayout
  if (resolvedLayout?.viewport) canvasViewport.value = { ...resolvedLayout.viewport }
  return resolvedLayout
}

const switchToVersion = async (version) => {
  if (!version) return
  let detail = version
  if (!detail.definitionJson) {
    try {
      const res = await api.getWorkflowVersion(currentWorkflow.value.id, version.id)
      if (res.data.code === 200) detail = res.data.data || version
    } catch (e) {
      console.error('加载版本详情失败', e)
      ElMessage.error('加载版本详情失败')
      return
    }
  }
  try {
    await ElMessageBox.confirm(
      '切换版本会用该版本的 definition + layout 覆盖当前画布内容（本地草稿布局会同步替换）。未保存的草稿修改将丢失，确定继续？',
      '切换版本',
      { confirmButtonText: '确定覆盖草稿', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  let layout = null
  if (detail.layoutJson) {
    try {
      layout = typeof detail.layoutJson === 'string' ? JSON.parse(detail.layoutJson) : detail.layoutJson
    } catch (_) {
      layout = null
    }
  }
  hydrateCanvasFromDefinition(detail.definitionJson, layout)
  viewingVersion.value = detail
  editorLocked.value = true
  showVersionDetail.value = false
  ElMessage.success(`已切换查看版本 #${detail.versionNo ?? detail.id}（只读，点“编辑”解锁）`)
}

const unlockEditing = () => {
  editorLocked.value = false
  ElMessage.success('已进入编辑模式')
}

const returnToDraft = async () => {
  const workflow = currentWorkflow.value
  if (workflow?.id) {
    try {
      const res = await api.getWorkflow(workflow.id)
      if (res.data.code === 200 && res.data.data) {
        currentWorkflow.value = { ...workflow, ...res.data.data }
        if (res.data.data.workflowDefinition) {
          const storedLayout = readStoredLayout(currentWorkflow.value)
          hydrateCanvasFromDefinition(res.data.data.workflowDefinition, storedLayout)
        }
      }
    } catch (e) {
      console.error('恢复草稿失败', e)
      ElMessage.error('恢复草稿失败')
      return
    }
  }
  viewingVersion.value = null
  editorLocked.value = false
  ElMessage.success('已返回草稿')
}

onMounted(() => {
  loadWorkflows()
  loadAgents()
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
.workflow-support-warning {
  margin-bottom: 12px;
}

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

/* Input bindings */
.binding-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.binding-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  background: rgba(26, 31, 78, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
}

.binding-key {
  width: 100%;
}

.binding-value :deep(textarea) {
  font-family: var(--font-mono, monospace);
  font-size: 11px;
}

.binding-row-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.binding-errors {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
}

.binding-error {
  font-size: 11px;
  color: var(--accent-red, #f87171);
  line-height: 1.4;
}

/* Variables panel */
.variables-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.variable-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  background: rgba(26, 31, 78, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
}

.variable-row-inline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.variable-type {
  flex: 1;
}

/* Version detail */
.version-tag {
  margin-left: 8px;
}

.canvas-title {
  display: flex;
  align-items: center;
}

.version-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
  font-size: 12px;
  color: var(--text-muted);
}

.version-hash {
  word-break: break-all;
}

.json-block {
  margin: 0;
  padding: 12px;
  max-height: 320px;
  overflow: auto;
  background: rgba(10, 16, 40, 0.65);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 11px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}

.version-detail-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.canvas-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
</style>
