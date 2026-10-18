export const WORKFLOW_NODE_TYPES = Object.freeze([
  { type: 'START', label: '开始', icon: '▶' },
  { type: 'AGENT', label: 'Agent节点', icon: '⚙' },
  { type: 'CONDITION', label: '条件分支', icon: '✖' },
  { type: 'END', label: '结束', icon: '■' }
])

export const WORKFLOW_SCHEMA_VERSION = 1

export const VARIABLE_TYPES = Object.freeze(['string', 'number', 'boolean', 'object', 'array'])

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
    inputs: {},
    inputBindings: [],
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

// --- Template references -------------------------------------------------

const REF_PATTERN = /\{\{\s*([^{}]+?)\s*\}\}/g

export const extractTemplateReferences = (value) => {
  const found = []
  const walk = (item) => {
    if (typeof item === 'string') {
      for (const match of item.matchAll(REF_PATTERN)) {
        found.push(match[1].trim())
      }
      return
    }
    if (Array.isArray(item)) {
      item.forEach(walk)
      return
    }
    if (item && typeof item === 'object') {
      Object.values(item).forEach(walk)
    }
  }
  walk(value)
  return found
}

/** Friendly errors for {{...}} refs. `nodeIds` is any iterable of node ids in the graph. */
export const validateTemplateReferences = (value, { variableNames = [], nodeIds = [] } = {}) => {
  const vars = new Set([...variableNames].map(name => String(name)))
  const nodes = new Set([...nodeIds].map(id => String(id)))
  const errors = []
  for (const path of extractTemplateReferences(value)) {
    if (path.startsWith('variables.')) {
      const name = path.slice('variables.'.length)
      if (!name) errors.push('变量引用缺少名称，应形如 {{variables.名称}}')
      else if (!vars.has(name)) errors.push(`引用的变量「${name}」未在工作流变量中声明`)
      continue
    }
    if (path.startsWith('nodes.')) {
      const parts = path.split('.')
      // nodes.<id>.outputs.<field>
      if (parts.length < 4 || parts[2] !== 'outputs' || !parts[1] || !parts[3]) {
        errors.push(`节点引用格式应为 {{nodes.<节点ID>.outputs.<字段>}}，当前是「${path}」`)
        continue
      }
      if (!nodes.has(parts[1])) errors.push(`引用的节点「${parts[1]}」不在当前编排图中`)
      continue
    }
    if (path.startsWith('inputs.')) {
      const field = path.slice('inputs.'.length)
      if (!field) errors.push('inputs 引用缺少字段名，应形如 {{inputs.字段}}')
      continue
    }
    errors.push(`不支持的引用「${path}」，仅支持 {{variables.*}}、{{nodes.*.outputs.*}}、{{inputs.*}}`)
  }
  return errors
}

export const formatReference = (path) => `{{${path}}}`

export const buildVariableReference = (name) => formatReference(`variables.${name}`)

export const buildNodeOutputReference = (nodeId, field = 'output') =>
  formatReference(`nodes.${nodeId}.outputs.${field}`)

export const buildInputReference = (field = 'field') => formatReference(`inputs.${field}`)

/** Append a reference at the caret (or at the end) of an existing value. */
export const insertTemplateReference = (value, reference, caret) => {
  const text = typeof value === 'string' ? value : ''
  if (typeof caret === 'number' && caret >= 0 && caret <= text.length) {
    return text.slice(0, caret) + reference + text.slice(caret)
  }
  return text + reference
}

// --- Input bindings -----------------------------------------------------

/** Map form ({key: value}) -> editable rows. */
export const inputsToBindings = (inputs) => {
  if (!inputs || typeof inputs !== 'object' || Array.isArray(inputs)) return []
  return Object.entries(inputs).map(([key, value]) => ({
    key,
    value: typeof value === 'string' ? value : JSON.stringify(value)
  }))
}

/** Editable rows -> map form for the definition. Empty keys are dropped. */
export const bindingsToInputs = (bindings) => {
  const inputs = {}
  for (const row of Array.isArray(bindings) ? bindings : []) {
    const key = typeof row?.key === 'string' ? row.key.trim() : ''
    if (!key) continue
    inputs[key] = row.value ?? ''
  }
  return inputs
}

// --- Variables ----------------------------------------------------------

export const normalizeVariables = (variables) => {
  const seen = new Set()
  const normalized = []
  for (const item of Array.isArray(variables) ? variables : []) {
    const name = typeof item?.name === 'string' ? item.name.trim() : ''
    if (!name || seen.has(name)) continue
    seen.add(name)
    const entry = {
      name,
      type: VARIABLE_TYPES.includes(item.type) ? item.type : 'string',
      required: !!item.required
    }
    const hasDefault = item.defaultValueText !== undefined
      ? String(item.defaultValueText).trim() !== ''
      : item.defaultValue !== undefined && item.defaultValue !== null && item.defaultValue !== ''
    if (hasDefault) {
      const raw = item.defaultValueText !== undefined ? item.defaultValueText : item.defaultValue
      entry.defaultValue = coerceVariableDefault(raw, entry.type)
    }
    if (item.description) entry.description = String(item.description)
    normalized.push(entry)
  }
  return normalized
}

const coerceVariableDefault = (raw, type) => {
  if (raw === null || raw === undefined) return undefined
  if (type === 'string') return String(raw)
  if (type === 'number') {
    const num = Number(raw)
    return Number.isFinite(num) ? num : raw
  }
  if (type === 'boolean') {
    if (typeof raw === 'boolean') return raw
    const text = String(raw).trim().toLowerCase()
    if (text === 'true') return true
    if (text === 'false') return false
    return raw
  }
  if (type === 'object' || type === 'array') {
    if (typeof raw !== 'string') return raw
    try {
      return JSON.parse(raw)
    } catch {
      return raw
    }
  }
  return raw
}

export const variablesToEditable = (variables) => (Array.isArray(variables) ? variables : []).map(item => ({
  name: item?.name || '',
  type: VARIABLE_TYPES.includes(item?.type) ? item.type : 'string',
  required: !!item?.required,
  defaultValueText: item?.defaultValue === undefined || item?.defaultValue === null
    ? ''
    : (typeof item.defaultValue === 'string' ? item.defaultValue : JSON.stringify(item.defaultValue)),
  description: item?.description || ''
}))

// --- Definition / layout split -----------------------------------------

const LAYOUT_FREE_NODE_FIELDS = [
  'id', 'type', 'label', 'targetAgent', 'agentId', 'params', 'inputs',
  'timeout', 'retryCount', 'condition', 'outputsSchema'
]

export const stripNodeLayout = (node) => {
  const clean = {}
  for (const field of LAYOUT_FREE_NODE_FIELDS) {
    if (node?.[field] !== undefined) clean[field] = node[field]
  }
  clean.id = String(node.id)
  const fromBindings = Array.isArray(node?.inputBindings) ? bindingsToInputs(node.inputBindings) : null
  if (fromBindings && Object.keys(fromBindings).length > 0) clean.inputs = fromBindings
  else if (node?.inputs && typeof node.inputs === 'object' && !Array.isArray(node.inputs)) clean.inputs = node.inputs
  else clean.inputs = {}
  return clean
}

/** Execution contract only — never contains canvas x/y. */
export const buildWorkflowDefinition = ({ nodes = [], edges = [], variables = [], timeoutSeconds = 1800 } = {}) => {
  const definition = {
    schemaVersion: WORKFLOW_SCHEMA_VERSION,
    timeoutSeconds,
    variables: normalizeVariables(variables),
    nodes: nodes.map(node => stripNodeLayout(node)),
    edges: edges.map(edge => ({
      source: String(edge.source),
      target: String(edge.target),
      ...(edge.condition ? { condition: edge.condition } : {})
    }))
  }
  for (const node of definition.nodes) {
    if (node.type !== 'CONDITION') continue
    const outgoing = definition.edges.filter(edge => edge.source === node.id)
    if (outgoing[0] && !outgoing[0].condition) outgoing[0].condition = 'true'
    if (outgoing[1] && !outgoing[1].condition) outgoing[1].condition = 'false'
  }
  return definition
}

export const buildWorkflowLayout = (nodes = [], viewport) => {
  const layout = {
    nodes: nodes.map(node => ({
      id: String(node.id),
      x: Number.isFinite(Number(node.x)) ? Math.round(Number(node.x)) : 0,
      y: Number.isFinite(Number(node.y)) ? Math.round(Number(node.y)) : 0
    }))
  }
  const vp = viewport && typeof viewport === 'object' ? viewport : {}
  const x = Number(vp.x)
  const y = Number(vp.y)
  const zoom = Number(vp.zoom)
  if (Number.isFinite(x) && Number.isFinite(y) && Number.isFinite(zoom)) {
    layout.viewport = { x, y, zoom }
  }
  return layout
}

/** Pull x/y out of a legacy definition node list into a layout object. */
export const extractLayoutFromNodes = (nodes = []) => buildWorkflowLayout(nodes)

/** Apply a layout snapshot onto definition nodes (returns new node objects). */
export const applyWorkflowLayout = (nodes = [], layout) => {
  const positions = new Map()
  for (const entry of Array.isArray(layout?.nodes) ? layout.nodes : []) {
    if (entry && entry.id !== undefined && Number.isFinite(Number(entry.x)) && Number.isFinite(Number(entry.y))) {
      positions.set(String(entry.id), { x: Math.round(Number(entry.x)), y: Math.round(Number(entry.y)) })
    }
  }
  return nodes.map(node => {
    const position = positions.get(String(node.id))
    return position ? { ...node, ...position } : { ...node, x: Number(node.x) || 0, y: Number(node.y) || 0 }
  })
}

export const parseWorkflowDefinition = (definition) => {
  let graph = definition
  if (typeof definition === 'string') {
    try {
      graph = JSON.parse(definition)
    } catch {
      graph = {}
    }
  }
  if (!graph || typeof graph !== 'object') graph = {}
  return {
    schemaVersion: graph.schemaVersion ?? WORKFLOW_SCHEMA_VERSION,
    timeoutSeconds: graph.timeoutSeconds ?? 1800,
    variables: Array.isArray(graph.variables) ? graph.variables : [],
    nodes: Array.isArray(graph.nodes) ? graph.nodes : [],
    edges: Array.isArray(graph.edges) ? graph.edges : []
  }
}

/** Collect every quick-insert candidate for the current node. */
export const buildReferenceSuggestions = ({ variables = [], nodes = [], currentNodeId, edges = [] } = {}) => {
  const suggestions = []
  for (const variable of variables) {
    const name = typeof variable === 'string' ? variable : variable?.name
    if (name) {
      suggestions.push({
        group: '变量',
        label: `变量 ${name}`,
        reference: buildVariableReference(name)
      })
    }
  }
  const current = currentNodeId === undefined || currentNodeId === null ? null : String(currentNodeId)
  const upstream = new Set()
  for (const edge of edges) {
    if (current !== null && String(edge.target) === current) upstream.add(String(edge.source))
  }
  const nodeIds = nodes
    .map(node => String(node.id))
    .filter(id => id !== current && (upstream.size === 0 || upstream.has(id)))
  for (const id of nodeIds) {
    suggestions.push({
      group: '上游输出',
      label: `节点 ${id} 输出`,
      reference: buildNodeOutputReference(id)
    })
  }
  suggestions.push({ group: '运行输入', label: '运行输入字段', reference: buildInputReference() })
  return suggestions
}
