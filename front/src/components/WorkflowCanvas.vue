<template>
  <div class="workflow-canvas-wrap" @drop.prevent="onDrop" @dragover.prevent>
    <VueFlow
      v-model:nodes="flowNodes"
      v-model:edges="flowEdges"
      :default-viewport="{ zoom: 1 }"
      :min-zoom="0.2"
      :max-zoom="2"
      fit-view-on-init
      @node-click="onNodeClick"
      @nodes-change="onNodesChange"
      @connect="onConnect"
      @pane-click="onPaneClick"
      @edge-click="onEdgeClick"
    >
      <template #node-workflow="props">
        <div
          class="wf-node"
          :class="[props.data.type?.toLowerCase(), { selected: props.selected }]"
        >
          <Handle type="target" :position="Position.Top" />
          <div class="wf-node-icon">{{ iconOf(props.data.type) }}</div>
          <div class="wf-node-label">{{ props.data.label }}</div>
          <div class="wf-node-type">{{ props.data.type }}</div>
          <Handle type="source" :position="Position.Bottom" />
        </div>
      </template>
    </VueFlow>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { VueFlow, Handle, Position, useVueFlow } from '@vue-flow/core'
import { applyWorkflowPositionChanges, createDroppedWorkflowNode } from '../utils/workflowCanvas.js'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  edges: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:nodes', 'update:edges', 'select-node', 'select-edge', 'clear-selection'])
const { screenToFlowCoordinate } = useVueFlow()

const flowNodes = computed({
  get: () => props.nodes.map(node => ({
    id: String(node.id),
    type: 'workflow',
    position: { x: Number(node.x) || 0, y: Number(node.y) || 0 },
    data: { ...node }
  })),
  set: (value) => {
    emit('update:nodes', value.map(item => ({
      ...item.data,
      id: item.id,
      x: Math.round(item.position.x),
      y: Math.round(item.position.y)
    })))
  }
})

const flowEdges = computed({
  get: () => props.edges.map((edge, index) => ({
    id: edge.id || `e-${edge.source}-${edge.target}-${index}`,
    source: String(edge.source),
    target: String(edge.target),
    label: edge.condition || '',
    data: { ...edge }
  })),
  set: (value) => {
    emit('update:edges', value.map(item => ({
      ...item.data,
      source: item.source,
      target: item.target
    })))
  }
})

const iconOf = (type) => ({ START: '▶', AGENT: '⚙', CONDITION: '✖', PARALLEL: '◎', END: '■' }[type] || '●')

const onNodeClick = ({ node }) => {
  const source = props.nodes.find(item => String(item.id) === String(node.id))
  if (source) emit('select-node', source)
}

const onNodesChange = (changes) => {
  // Dragging a selection and keyboard nudges both emit position changes for every affected node.
  const nodes = applyWorkflowPositionChanges(props.nodes, changes)
  if (nodes !== props.nodes) emit('update:nodes', nodes)
}

const onConnect = (params) => {
  const source = String(params.source)
  const target = String(params.target)
  if (!source || !target || source === target) return
  if (props.edges.some(edge => String(edge.source) === source && String(edge.target) === target)) return
  emit('update:edges', [...props.edges, { source, target }])
}

const onEdgeClick = ({ edge }) => {
  const source = props.edges.find(item =>
    String(item.source) === String(edge.source) && String(item.target) === String(edge.target))
  if (source) emit('select-edge', source)
}

const onPaneClick = () => emit('clear-selection')

const onDrop = (event) => {
  const raw = event.dataTransfer?.getData('application/workflow-node')
  if (!raw) return
  let payload
  try {
    payload = JSON.parse(raw)
  } catch {
    return
  }
  const node = createDroppedWorkflowNode(payload, { x: event.clientX, y: event.clientY }, screenToFlowCoordinate, props.nodes)
  if (node) emit('update:nodes', [...props.nodes, node])
}
</script>

<style scoped>
.workflow-canvas-wrap {
  width: 100%;
  height: 100%;
  min-height: 420px;
  border-radius: 8px;
  overflow: hidden;
  background: rgba(10, 16, 40, 0.55);
}

.wf-node {
  min-width: 120px;
  padding: 10px 12px 8px;
  border-radius: 8px;
  border: 1px solid rgba(0, 240, 255, 0.35);
  background: rgba(18, 28, 64, 0.92);
  color: #e8f4ff;
  font-size: 12px;
  text-align: center;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.28);
}

.wf-node.selected {
  border-color: #00f0ff;
  box-shadow: 0 0 0 2px rgba(0, 240, 255, 0.35);
}

.wf-node.start { border-color: rgba(80, 250, 160, 0.55); }
.wf-node.end { border-color: rgba(255, 120, 160, 0.55); }
.wf-node.condition { border-color: rgba(255, 200, 80, 0.55); }

.wf-node-icon { font-size: 16px; line-height: 1.2; }
.wf-node-label { font-weight: 600; margin: 4px 0 2px; word-break: break-all; }
.wf-node-type { color: rgba(180, 210, 255, 0.7); font-size: 10px; letter-spacing: 0.04em; }

:deep(.vue-flow__handle) {
  width: 8px;
  height: 8px;
  background: #00f0ff;
  border: 1px solid rgba(255, 255, 255, 0.4);
}

:deep(.vue-flow__edge-path) {
  stroke: #00f0ff;
  stroke-width: 1.5;
}

:deep(.vue-flow__controls-button) {
  background: rgba(18, 28, 64, 0.9);
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
  fill: #cfe8ff;
}
</style>
