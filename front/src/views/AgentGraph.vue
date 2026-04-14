<template>
  <div class="page-container">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">Agent 调用关系图谱</h2>
        <span class="total-count mono">共 {{ nodeCount }} 个Agent，{{ edgeCount }} 条调用关系</span>
      </div>
      <div class="header-actions">
        <el-radio-group v-model="statusFilter" class="status-filter">
          <el-radio-button label="all">全部</el-radio-button>
          <el-radio-button label="online">在线</el-radio-button>
          <el-radio-button label="offline">离线</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="loadGraphData" class="refresh-btn">
          <Refresh class="btn-icon" /> 刷新
        </el-button>
      </div>
    </div>

    <!-- Graph Card -->
    <div class="graph-card glass-card">
      <div ref="chartRef" class="chart-container"></div>
    </div>

    <!-- Node Detail Panel -->
    <el-drawer v-model="nodeDrawerVisible" title="Agent 详情" size="400px" direction="rtl">
      <div v-if="selectedNode" class="node-detail">
        <div class="detail-item">
          <span class="detail-label">Agent ID</span>
          <span class="detail-value mono">#{{ selectedNode.id }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">名称</span>
          <span class="detail-value">{{ selectedNode.name }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">状态</span>
          <span class="status-badge" :class="selectedNode.status">
            {{ getStatusText(selectedNode.status) }}
          </span>
        </div>
        <div class="detail-item">
          <span class="detail-label">实例ID</span>
          <span class="detail-value mono">{{ selectedNode.instanceId || '-' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">最后心跳</span>
          <span class="detail-value mono">{{ formatTime(selectedNode.lastHeartbeat) }}</span>
        </div>
      </div>
    </el-drawer>

    <!-- Edge Detail Panel -->
    <el-drawer v-model="edgeDrawerVisible" title="调用统计" size="400px" direction="rtl">
      <div v-if="selectedEdge" class="edge-detail">
        <div class="detail-item">
          <span class="detail-label">源Agent</span>
          <span class="detail-value mono">#{{ selectedEdge.source }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">目标Agent</span>
          <span class="detail-value mono">#{{ selectedEdge.target }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">调用次数</span>
          <span class="detail-value mono">{{ selectedEdge.callCount || 0 }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">调用频率</span>
          <span class="detail-value">{{ selectedEdge.frequency || 0 }}次/分钟</span>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import { Refresh } from '@element-plus/icons-vue'
import api from '../api'

const chartRef = ref(null)
let chart = null
let pollInterval = null

const graphData = ref({ nodes: [], edges: [] })
const statusFilter = ref('all')
const nodeDrawerVisible = ref(false)
const edgeDrawerVisible = ref(false)
const selectedNode = ref(null)
const selectedEdge = ref(null)

const nodeCount = computed(() => {
  return statusFilter.value === 'all'
    ? graphData.value.nodes.length
    : graphData.value.nodes.filter(n => n.status === statusFilter.value).length
})

const edgeCount = computed(() => graphData.value.edges.length)

const statusColors = {
  online: '#10b981',
  offline: '#9ca3af',
  error: '#ef4444'
}

const getStatusText = (status) => {
  const map = { online: '在线', offline: '离线', error: '错误' }
  return map[status] || status
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
    second: '2-digit',
    hour12: false
  })
}

const getNodeColor = (status) => {
  return statusColors[status] || statusColors.offline
}

const loadGraphData = async () => {
  try {
    const res = await api.getAgentGraph()
    if (res.data.code === 200) {
      graphData.value = res.data.data || { nodes: [], edges: [] }
      updateChart()
    }
  } catch (e) {
    console.error('加载图谱数据失败', e)
  }
}

const getFilteredNodes = () => {
  if (statusFilter.value === 'all') {
    return graphData.value.nodes
  }
  return graphData.value.nodes.filter(n => n.status === statusFilter.value)
}

const updateChart = () => {
  if (!chart) return

  const filteredNodes = getFilteredNodes()
  const nodeIds = new Set(filteredNodes.map(n => n.id))

  const filteredEdges = graphData.value.edges.filter(
    e => nodeIds.has(e.source) && nodeIds.has(e.target)
  )

  const maxCallCount = Math.max(...filteredEdges.map(e => e.callCount || 1), 1)

  const nodes = filteredNodes.map(node => ({
    id: node.id,
    name: node.name,
    symbolSize: 60,
    itemStyle: { color: getNodeColor(node.status) },
    status: node.status,
    instanceId: node.instanceId,
    lastHeartbeat: node.lastHeartbeat
  }))

  const edges = filteredEdges.map(edge => ({
    source: edge.source,
    target: edge.target,
    lineStyle: {
      width: Math.max(1, Math.ceil((edge.callCount / maxCallCount) * 5)),
      color: '#6366f1',
      opacity: 0.6
    },
    callCount: edge.callCount,
    frequency: edge.frequency
  }))

  chart.setOption({
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(17, 17, 17, 0.9)',
      borderColor: '#333',
      textStyle: { color: '#fff' },
      formatter: (params) => {
        if (params.dataType === 'node') {
          return `<strong>${params.data.name}</strong><br/>状态: ${getStatusText(params.data.status)}`
        }
        if (params.dataType === 'edge') {
          return `调用次数: ${params.data.callCount || 0}`
        }
        return ''
      }
    },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      force: {
        repulsion: 300,
        edgeLength: 120,
        layoutAnimation: true
      },
      symbol: 'circle',
      data: nodes,
      edges: edges,
      label: {
        show: true,
        position: 'bottom',
        formatter: '{b}',
        color: '#e5e7eb',
        fontSize: 12
      },
      lineStyle: {
        curveness: 0.2
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 3 },
        itemStyle: { borderWidth: 3, borderColor: '#fff' }
      }
    }]
  })
}

const initChart = () => {
  if (!chartRef.value) return

  chart = echarts.init(chartRef.value)

  chart.on('click', (params) => {
    if (params.dataType === 'node') {
      selectedNode.value = {
        id: params.data.id,
        name: params.data.name,
        status: params.data.status,
        instanceId: params.data.instanceId,
        lastHeartbeat: params.data.lastHeartbeat
      }
      nodeDrawerVisible.value = true
    } else if (params.dataType === 'edge') {
      selectedEdge.value = params.data
      edgeDrawerVisible.value = true
    }
  })

  window.addEventListener('resize', () => chart?.resize())
}

watch(statusFilter, () => {
  updateChart()
})

onMounted(() => {
  initChart()
  loadGraphData()
  pollInterval = setInterval(loadGraphData, 10000)
})

onUnmounted(() => {
  if (pollInterval) clearInterval(pollInterval)
  chart?.dispose()
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
  align-items: center;
}

.status-filter {
  --el-radio-button-checked-bg-color: var(--accent-cyan);
  --el-radio-button-checked-border-color: var(--accent-cyan);
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
}

.btn-icon {
  width: 14px;
  height: 14px;
}

.graph-card {
  height: calc(100vh - 220px);
  min-height: 500px;
  padding: 0;
  overflow: hidden;
}

.chart-container {
  width: 100%;
  height: 100%;
}

/* Detail Panel */
.node-detail,
.edge-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 0 8px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-label {
  font-size: 12px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.detail-value {
  font-size: 14px;
  color: var(--text-primary);
}

.status-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  width: fit-content;
}

.status-badge.online {
  background: rgba(16, 185, 129, 0.15);
  color: var(--accent-green);
}

.status-badge.offline {
  background: rgba(156, 163, 175, 0.15);
  color: #9ca3af;
}

.status-badge.error {
  background: rgba(239, 68, 68, 0.15);
  color: var(--accent-red);
}
</style>
