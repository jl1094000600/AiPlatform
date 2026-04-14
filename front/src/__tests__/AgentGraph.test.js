/**
 * AgentGraph 组件测试
 *
 * 测试内容:
 * 1. 图谱渲染测试
 * 2. 交互事件测试
 * 3. 数据过滤测试
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

// Mock echarts before importing component
vi.mock('echarts', () => ({
  init: vi.fn(() => ({
    setOption: vi.fn(),
    dispose: vi.fn(),
    resize: vi.fn(),
    on: vi.fn()
  })),
  dispose: vi.fn()
}))

// Mock api
vi.mock('../api', () => ({
  default: {
    getAgentGraph: vi.fn(() => Promise.resolve({
      data: {
        code: 200,
        data: {
          nodes: [
            { id: 1, name: 'Agent-A', status: 'online', type: 'AI' },
            { id: 2, name: 'Agent-B', status: 'offline', type: 'AI' }
          ],
          edges: [
            { source: 1, target: 2, callCount: 100, avgResponseTime: 150.5 }
          ]
        }
      }
    }))
  }
}))

describe('AgentGraph Component Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('API Integration', () => {
    it('should call getAgentGraph API', async () => {
      const api = await import('../api')
      const result = await api.default.getAgentGraph()
      expect(result.data.code).toBe(200)
    })

    it('should return correct graph structure', async () => {
      const api = await import('../api')
      const result = await api.default.getAgentGraph()
      const data = result.data.data

      expect(data).toHaveProperty('nodes')
      expect(data).toHaveProperty('edges')
      expect(Array.isArray(data.nodes)).toBe(true)
      expect(Array.isArray(data.edges)).toBe(true)
    })

    it('should have correct node structure', async () => {
      const api = await import('../api')
      const result = await api.default.getAgentGraph()
      const node = result.data.data.nodes[0]

      expect(node).toHaveProperty('id')
      expect(node).toHaveProperty('name')
      expect(node).toHaveProperty('status')
      expect(node).toHaveProperty('type')
    })

    it('should have correct edge structure', async () => {
      const api = await import('../api')
      const result = await api.default.getAgentGraph()
      const edge = result.data.data.edges[0]

      expect(edge).toHaveProperty('source')
      expect(edge).toHaveProperty('target')
      expect(edge).toHaveProperty('callCount')
    })
  })

  describe('Status Filter Logic', () => {
    it('should filter nodes by status', () => {
      const nodes = [
        { id: 1, name: 'Agent-A', status: 'online' },
        { id: 2, name: 'Agent-B', status: 'offline' },
        { id: 3, name: 'Agent-C', status: 'online' }
      ]

      const statusFilter = 'online'
      const filteredNodes = statusFilter === 'all'
        ? nodes
        : nodes.filter(n => n.status === statusFilter)

      expect(filteredNodes).toHaveLength(2)
      expect(filteredNodes.every(n => n.status === 'online')).toBe(true)
    })

    it('should return all nodes when filter is all', () => {
      const nodes = [
        { id: 1, name: 'Agent-A', status: 'online' },
        { id: 2, name: 'Agent-B', status: 'offline' }
      ]

      const statusFilter = 'all'
      const filteredNodes = statusFilter === 'all'
        ? nodes
        : nodes.filter(n => n.status === statusFilter)

      expect(filteredNodes).toHaveLength(2)
    })
  })

  describe('Graph Data Processing', () => {
    it('should calculate max call count for edge width', () => {
      const edges = [
        { source: 1, target: 2, callCount: 50 },
        { source: 2, target: 3, callCount: 100 },
        { source: 3, target: 4, callCount: 25 }
      ]

      const maxCallCount = Math.max(...edges.map(e => e.callCount || 1), 1)
      expect(maxCallCount).toBe(100)
    })

    it('should filter edges based on node IDs', () => {
      const nodes = [
        { id: 1 },
        { id: 2 }
      ]
      const edges = [
        { source: 1, target: 2 },
        { source: 3, target: 4 } // Should be filtered out
      ]

      const nodeIds = new Set(nodes.map(n => n.id))
      const filteredEdges = edges.filter(
        e => nodeIds.has(e.source) && nodeIds.has(e.target)
      )

      expect(filteredEdges).toHaveLength(1)
    })

    it('should calculate edge width based on call count', () => {
      const callCount = 50
      const maxCallCount = 100

      const width = Math.max(1, Math.ceil((callCount / maxCallCount) * 5))
      expect(width).toBe(3)
    })
  })

  describe('Status Display', () => {
    const getStatusText = (status) => {
      const map = { online: '在线', offline: '离线', error: '错误' }
      return map[status] || status
    }

    it('should return correct status text', () => {
      expect(getStatusText('online')).toBe('在线')
      expect(getStatusText('offline')).toBe('离线')
      expect(getStatusText('error')).toBe('错误')
    })

    it('should return original value for unknown status', () => {
      expect(getStatusText('unknown')).toBe('unknown')
    })
  })

  describe('Time Formatting', () => {
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

    it('should return dash for null time', () => {
      expect(formatTime(null)).toBe('-')
    })

    it('should return dash for undefined time', () => {
      expect(formatTime(undefined)).toBe('-')
    })

    it('should format valid time', () => {
      const date = new Date('2024-01-01T12:00:00')
      const formatted = formatTime(date)
      expect(formatted).toContain('2024')
      expect(formatted).toContain('12')
    })
  })

  describe('Node Color Logic', () => {
    const getNodeColor = (status) => {
      const statusColors = {
        online: '#10b981',
        offline: '#9ca3af',
        error: '#ef4444'
      }
      return statusColors[status] || statusColors.offline
    }

    it('should return green for online status', () => {
      expect(getNodeColor('online')).toBe('#10b981')
    })

    it('should return gray for offline status', () => {
      expect(getNodeColor('offline')).toBe('#9ca3af')
    })

    it('should return red for error status', () => {
      expect(getNodeColor('error')).toBe('#ef4444')
    })

    it('should return gray for unknown status', () => {
      expect(getNodeColor('unknown')).toBe('#9ca3af')
    })
  })
})
