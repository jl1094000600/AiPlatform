export const WORKFLOW_NODE_TYPES = Object.freeze([
  { type: 'START', label: '开始', icon: '▶' },
  { type: 'AGENT', label: 'Agent节点', icon: '⚙' },
  { type: 'CONDITION', label: '条件分支', icon: '✖' },
  { type: 'END', label: '结束', icon: '■' }
])

const supportedTypes = new Set(WORKFLOW_NODE_TYPES.map(node => node.type))

export const workflowSupportError = (definition) => {
  let graph
  try {
    graph = typeof definition === 'string' ? JSON.parse(definition) : definition
  } catch {
    return '编排定义无法解析，禁止保存和运行'
  }
  if (!graph || !Array.isArray(graph.nodes)) return '编排定义无效，禁止保存和运行'
  const unsupported = [...new Set(graph.nodes
    .filter(node => !supportedTypes.has(node?.type))
    .map(node => node?.type || '未知类型'))]
  return unsupported.length
    ? `暂未开放的节点：${unsupported.join('、')}。原图已保留，请改为开始 / Agent / 条件 / 结束节点后再保存；当前图禁止保存和运行`
    : ''
}

export const createDroppedWorkflowNode = (payload, screenPoint, screenToFlowCoordinate, nodes) => {
  if (!payload || !supportedTypes.has(payload.type) || !payload.id) return null
  const id = String(payload.id)
  if (nodes.some(node => String(node.id) === id)) return null
  // Vue Flow accounts for both the canvas bounds and the current viewport transform.
  const position = screenToFlowCoordinate(screenPoint)
  if (!Number.isFinite(position.x) || !Number.isFinite(position.y)) return null
  return {
    id,
    type: payload.type,
    label: payload.label || WORKFLOW_NODE_TYPES.find(node => node.type === payload.type).label,
    x: Math.round(position.x - 60),
    y: Math.round(position.y - 24),
    agentId: null,
    params: {},
    timeout: 30,
    retryCount: 0
  }
}

export const applyWorkflowPositionChanges = (nodes, changes) => {
  const positions = new Map()
  for (const change of changes) {
    if (change.type === 'position' && Number.isFinite(change.position?.x) && Number.isFinite(change.position?.y)) {
      positions.set(String(change.id), {
        x: Math.round(change.position.x),
        y: Math.round(change.position.y)
      })
    }
  }
  let updated = false
  const next = nodes.map(node => {
    const position = positions.get(String(node.id))
    if (!position || (node.x === position.x && node.y === position.y)) return node
    updated = true
    return { ...node, ...position }
  })
  return updated ? next : nodes
}
