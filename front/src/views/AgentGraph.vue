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
        <el-button @click="toggleFullscreen" class="fullscreen-btn" :title="isFullscreen ? '退出全屏' : '全屏模式'">
          <FullScreen v-if="!isFullscreen" class="btn-icon" />
          <Close v-else class="btn-icon" />
        </el-button>
        <el-button @click="handleExport" class="export-btn">
          <Download class="btn-icon" /> 导出
        </el-button>
      </div>
    </div>

    <!-- Graph Card -->
    <div class="graph-card glass-card" :class="{ 'fullscreen-mode': isFullscreen }">
      <!-- Zoom Controls -->
      <div class="zoom-controls">
        <el-button circle size="small" @click="zoomIn" title="放大">
          <ZoomIn class="btn-icon-sm" />
        </el-button>
        <el-button circle size="small" @click="zoomOut" title="缩小">
          <ZoomOut class="btn-icon-sm" />
        </el-button>
        <el-button circle size="small" @click="resetZoom" title="重置">
          <RefreshRight class="btn-icon-sm" />
        </el-button>
        <span class="zoom-level mono">{{ zoomLevel }}%</span>
      </div>

      <!-- Realtime Call Chain Indicator -->
      <div v-if="activeCallChains.length > 0" class="call-chain-indicator">
        <span class="pulse-dot"></span>
        <span class="indicator-text">实时调用中 ({{ activeCallChains.length }})</span>
      </div>

      <div ref="chartRef" class="chart-container"></div>

      <!-- Minimap -->
      <div ref="minimapRef" class="minimap-container"></div>
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
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import { Refresh, Download, FullScreen, Close, ZoomIn, ZoomOut, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const chartRef = ref(null)
const minimapRef = ref(null)
let chart = null
let minimap = null
let pollInterval = null
let realtimeInterval = null
let zoomTimer = null

const graphData = ref({ nodes: [], edges: [] })
const statusFilter = ref('all')
const nodeDrawerVisible = ref(false)
const edgeDrawerVisible = ref(false)
const selectedNode = ref(null)
const selectedEdge = ref(null)
const isFullscreen = ref(false)
const zoomLevel = ref(100)
const activeCallChains = ref([])

const nodeCount = computed(() => {
  return statusFilter.value === 'all'
    ? graphData.value.nodes.length
    : graphData.value.nodes.filter(n => n.status === statusFilter.value).length
})

const edgeCount = computed(() => graphData.value.edges.length)

const statusColors = {
  online: '#00ff88',
  offline: '#9ca3af',
  error: '#ff6b9d'
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

const loadRealtimeCallChains = async () => {
  try {
    // Fetch recent executions to get active call chains
    const res = await api.getRealtimeData()
    if (res.data.code === 200) {
      // Update online agents count and active chains
      const data = res.data.data || {}
      activeCallChains.value = data.activeCallChains || []
    }
  } catch (e) {
    console.error('获取实时调用链失败', e)
  }
}

const handleExport = async () => {
  try {
    const res = await api.exportGraph()
    if (res.data.code === 200) {
      const data = res.data.data
      const json = JSON.stringify(data, null, 2)
      const blob = new Blob([json], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `agent-graph-${new Date().toISOString().slice(0, 10)}.json`
      link.click()
      URL.revokeObjectURL(url)
      ElMessage.success('导出成功')
    }
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

const toggleFullscreen = () => {
  isFullscreen.value = !isFullscreen.value
  nextTick(() => chart?.resize())
}

const zoomIn = () => {
  if (!chart) return
  const option = chart.getOption()
  const currentZoom = option.series[0].zoom || 1
  chart.setOption({
    series: [{ zoom: Math.min(currentZoom * 1.2, 3) }]
  })
  updateZoomLevel()
}

const zoomOut = () => {
  if (!chart) return
  const option = chart.getOption()
  const currentZoom = option.series[0].zoom || 1
  chart.setOption({
    series: [{ zoom: Math.max(currentZoom / 1.2, 0.3) }]
  })
  updateZoomLevel()
}

const resetZoom = () => {
  if (!chart) return
  chart.setOption({
    series: [{ zoom: 1 }]
  })
  updateZoomLevel()
}

const updateZoomLevel = () => {
  if (!chart) return
  const option = chart.getOption()
  zoomLevel.value = Math.round((option.series[0].zoom || 1) * 100)
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
    symbolSize: 70,
    itemStyle: {
      color: {
        type: 'radial',
        x: 0.5,
        y: 0.5,
        r: 0.6,
        colorStops: [
          { offset: 0, color: getNodeColor(node.status) },
          { offset: 0.8, color: getNodeColor(node.status) },
          { offset: 1, color: 'rgba(0,0,0,0.3)' }
        ]
      },
      shadowColor: getNodeColor(node.status),
      shadowBlur: 20
    },
    status: node.status,
    instanceId: node.instanceId,
    lastHeartbeat: node.lastHeartbeat
  }))

  const edges = filteredEdges.map(edge => {
    // Check if this edge is in active call chains
    const isActive = activeCallChains.value.some(
      chain => chain.source === edge.source && chain.target === edge.target
    )
    return {
      source: edge.source,
      target: edge.target,
      lineStyle: {
        width: Math.max(2, Math.ceil((edge.callCount / maxCallCount) * 6)),
        color: isActive
          ? { type: 'linear', x: 0, y: 0, x2: 1, y2: 0, colorStops: [
              { offset: 0, color: '#00ff88' },
              { offset: 0.5, color: '#00f0ff' },
              { offset: 1, color: '#00ff88' }
            ] }
          : { type: 'linear', x: 0, y: 0, x2: 1, y2: 0, colorStops: [
              { offset: 0, color: '#8b5cf6' },
              { offset: 0.5, color: '#00f0ff' },
              { offset: 1, color: '#ff00aa' }
            ] },
        opacity: 0.7,
        curveness: 0.2
      },
      // Flow animation for active edges
      lineStyle2: isActive ? { shadowBlur: 15, shadowColor: '#00f0ff' } : {},
      callCount: edge.callCount,
      frequency: edge.frequency
    }
  })

  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(17, 17, 17, 0.95)',
      borderColor: 'rgba(0, 240, 255, 0.3)',
      borderWidth: 1,
      textStyle: { color: '#fff' },
      extraCssText: 'backdrop-filter: blur(10px); border-radius: 12px;',
      formatter: (params) => {
        if (params.dataType === 'node') {
          return `<div style="padding: 8px 0;">
            <strong style="color: #00f0ff; font-size: 14px;">${params.data.name}</strong><br/>
            <span style="color: #888; font-size: 11px;">状态: ${getStatusText(params.data.status)}</span><br/>
            <span style="color: #888; font-size: 11px;">ID: #${params.data.id}</span>
          </div>`
        }
        if (params.dataType === 'edge') {
          return `<div style="padding: 8px 0;">
            <span style="color: #8b5cf6;">调用统计</span><br/>
            <span style="color: #00f0ff; font-size: 14px;">${params.data.callCount || 0}</span> 次调用
          </div>`
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
        repulsion: 350,
        edgeLength: 150,
        layoutAnimation: true,
        gravity: 0.1
      },
      symbol: 'circle',
      data: nodes,
      edges: edges,
      label: {
        show: true,
        position: 'bottom',
        formatter: '{b}',
        color: '#e5e7eb',
        fontSize: 12,
        fontFamily: 'Outfit, sans-serif'
      },
      lineStyle: {
        curveness: 0.25
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 4, color: '#00f0ff' },
        itemStyle: { borderWidth: 4, borderColor: '#fff', shadowBlur: 30 }
      },
      // Edge flow animation for active call chains
      edgeEffect: {
        show: true,
        period: 2,
        scale: 2,
        shake: true,
        effectType: 'ripple'
      },
      animationDuration: 1500,
      animationEasing: 'cubicOut'
    }]
  })
}

const initChart = () => {
  if (!chartRef.value) return

  chart = echarts.init(chartRef.value)

  // Double-click to show agent detail
  chart.on('dblclick', (params) => {
    if (params.dataType === 'node') {
      selectedNode.value = {
        id: params.data.id,
        name: params.data.name,
        status: params.data.status,
        instanceId: params.data.instanceId,
        lastHeartbeat: params.data.lastHeartbeat
      }
      nodeDrawerVisible.value = true
    }
  })

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

  // Right-click context menu
  chart.on('contextmenu', (params) => {
    if (params.dataType === 'node') {
      selectedNode.value = {
        id: params.data.id,
        name: params.data.name,
        status: params.data.status,
        instanceId: params.data.instanceId,
        lastHeartbeat: params.data.lastHeartbeat
      }
      nodeDrawerVisible.value = true
    }
  })

  window.addEventListener('resize', () => chart?.resize())
}

const initMinimap = () => {
  if (!minimapRef.value) return
  minimap = echarts.init(minimapRef.value)
  minimap.setOption({
    backgroundColor: 'rgba(26, 31, 78, 0.6)',
    series: [{
      type: 'graph',
      layout: 'force',
      roam: false,
      force: { repulsion: 100, edgeLength: 50 },
      symbol: 'circle',
      symbolSize: 10,
      data: [],
      edges: [],
      lineStyle: { opacity: 0.3 }
    }]
  })

  // Link minimap with main chart
  chart?.on('updateAxis', () => {
    if (minimap && chart) {
      minimap.setOption({
        tooltip: { show: false },
        series: [{
          data: chart.getOption().series[0].data,
          edges: chart.getOption().series[0].edges
        }]
      })
    }
  })
}

watch(statusFilter, () => {
  updateChart()
})

// Keyboard shortcuts
const handleKeydown = (e) => {
  if (e.key === 'Escape' && isFullscreen.value) {
    isFullscreen.value = false
    nextTick(() => chart?.resize())
  }
}

onMounted(() => {
  initChart()
  loadGraphData()
  // Poll for graph data every 10 seconds
  pollInterval = setInterval(loadGraphData, 10000)
  // Poll for realtime call chains every 5 seconds
  realtimeInterval = setInterval(loadRealtimeCallChains, 5000)
  // Listen for keyboard shortcuts
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  if (pollInterval) clearInterval(pollInterval)
  if (realtimeInterval) clearInterval(realtimeInterval)
  window.removeEventListener('keydown', handleKeydown)
  chart?.dispose()
  minimap?.dispose()
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

.export-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: rgba(16, 185, 129, 0.1);
  border-color: rgba(16, 185, 129, 0.3);
  color: var(--accent-green);
}

.export-btn:hover {
  background: rgba(16, 185, 129, 0.2);
  border-color: var(--accent-green);
}

.btn-icon {
  width: 14px;
  height: 14px;
}

.btn-icon-sm {
  width: 12px;
  height: 12px;
}

.graph-card {
  height: calc(100vh - 220px);
  min-height: 500px;
  padding: 0;
  overflow: hidden;
  position: relative;
}

.graph-card.fullscreen-mode {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  height: 100vh;
  border-radius: 0;
}

/* Zoom Controls */
.zoom-controls {
  position: absolute;
  top: 16px;
  left: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(26, 31, 78, 0.8);
  backdrop-filter: blur(8px);
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  z-index: 10;
}

.zoom-level {
  font-size: 12px;
  color: var(--text-muted);
  min-width: 40px;
  text-align: center;
}

/* Realtime Call Chain Indicator */
.call-chain-indicator {
  position: absolute;
  top: 16px;
  right: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(0, 255, 136, 0.1);
  backdrop-filter: blur(8px);
  border-radius: 8px;
  border: 1px solid rgba(0, 255, 136, 0.3);
  z-index: 10;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--neon-green);
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.2); }
}

.indicator-text {
  font-size: 12px;
  color: var(--neon-green);
  font-weight: 500;
}

/* Minimap */
.minimap-container {
  position: absolute;
  right: 16px;
  bottom: 16px;
  width: 150px;
  height: 100px;
  background: rgba(26, 31, 78, 0.6);
  backdrop-filter: blur(8px);
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  z-index: 10;
}

/* Fullscreen Button */
.fullscreen-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: rgba(124, 58, 237, 0.1);
  border-color: rgba(124, 58, 237, 0.3);
  color: var(--accent-purple);
}

.fullscreen-btn:hover {
  background: rgba(124, 58, 237, 0.2);
  border-color: var(--accent-purple);
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
  background: rgba(0, 255, 136, 0.15);
  color: var(--neon-green);
}

.status-badge.offline {
  background: rgba(156, 163, 175, 0.15);
  color: #9ca3af;
}

.status-badge.error {
  background: rgba(255, 107, 157, 0.15);
  color: var(--neon-pink);
}
</style>
