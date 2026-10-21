import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { computed, createRenderer, defineComponent, nextTick, reactive, ref, watch } from 'vue'
import { useVueFlow } from '@vue-flow/core'
import {
  WORKFLOW_NODE_TYPES, workflowSupportError, createDroppedWorkflowNode, applyWorkflowPositionChanges,
  extractTemplateReferences, validateTemplateReferences, inputsToBindings, bindingsToInputs,
  normalizeVariables, variablesToEditable, buildWorkflowDefinition, buildWorkflowLayout,
  applyWorkflowLayout, extractLayoutFromNodes, parseWorkflowDefinition, insertTemplateReference,
  buildReferenceSuggestions, WORKFLOW_SCHEMA_VERSION, VARIABLE_TYPES
} from '../utils/workflowCanvas.js'

let passed = 0
const check = async (name, run) => {
  await run()
  passed += 1
}

const renderer = createRenderer({
  patchProp() {}, insert() {}, remove() {}, createElement: () => ({}), createText: () => ({}),
  createComment: () => ({}), setText() {}, setElementText() {}, parentNode() {}, nextSibling() {}
})

// Run the actual canvas setup with a Vue renderer; the viewport converter comes from Vue Flow.
const canvasSource = readFileSync(new URL('../components/WorkflowCanvas.vue', import.meta.url), 'utf8')
const stripImports = (script) => script
  .replace(/import\s+['"][^'"]+['"];?/g, '')
  .replace(/import\s+[\s\S]*?from\s+['"][^'"]+['"];?/g, '')
const canvasScript = stripImports(canvasSource.split('<script setup>')[1].split('</script>')[0])
const setupCanvas = new Function(
  'computed', 'useVueFlow', 'defineProps', 'defineEmits', 'applyWorkflowPositionChanges', 'createDroppedWorkflowNode',
  `${canvasScript}\nreturn { onDrop, onNodesChange, flowNodes };`
)

const createCanvas = () => {
  let canvas
  let flow
  const props = reactive({ nodes: [], edges: [] })
  const app = renderer.createApp(defineComponent({
    setup() {
      canvas = setupCanvas(computed, () => {
        flow = useVueFlow()
        return flow
      }, () => props, () => (event, value) => {
        if (event === 'update:nodes') props.nodes = value
        if (event === 'update:edges') props.edges = value
      }, applyWorkflowPositionChanges, createDroppedWorkflowNode)
      watch(canvas.flowNodes, nodes => flow.setNodes(nodes), { immediate: true })
      flow.onNodesChange(canvas.onNodesChange)
      return () => null
    }
  }))
  app.mount({})
  flow.vueFlowRef.value = { getBoundingClientRect: () => ({ x: 100, y: 80 }) }
  flow.d3Selection.value = {}
  flow.d3Zoom.value = { scaleExtent() { return this }, translateExtent() { return this } }
  flow.dimensions.value = { width: 900, height: 600 }
  return { props, canvas, flow, close: () => app.unmount() }
}

const editorSource = readFileSync(new URL('../views/WorkflowEditor.vue', import.meta.url), 'utf8')
const editorScript = stripImports(editorSource.split('<script setup>')[1].split('</script>')[0])
const setupEditor = new Function(
  'computed', 'ref', 'onMounted', 'nextTick', 'api', 'hasPermission', 'ElMessage', 'ElMessageBox',
  'WORKFLOW_NODE_TYPES', 'VARIABLE_TYPES', 'workflowSupportError',
  'buildWorkflowDefinition', 'buildWorkflowLayout', 'applyWorkflowLayout', 'extractLayoutFromNodes',
  'parseWorkflowDefinition', 'inputsToBindings', 'bindingsToInputs', 'normalizeVariables',
  'variablesToEditable', 'validateTemplateReferences', 'insertTemplateReference', 'buildReferenceSuggestions',
  `${editorScript}\nreturn { workflowNodes, workflowEdges, workflowVariables, currentWorkflow, workflowSupportMessage, saveWorkflow, triggerWorkflow, publishVersion, collectReferenceErrors, selectedNode, selectedNodeBindingErrors, addBinding, removeBinding, insertRefIntoBinding, addVariable, removeVariable };`
)
const createEditor = (definition) => {
  const calls = []
  const warnings = []
  const response = (data) => Promise.resolve({ data: { code: 200, data } })
  const editor = setupEditor(computed, ref, () => {}, nextTick, {
    getWorkflow: (id) => { calls.push(['get', id]); return response({ id, status: 0, workflowDefinition: definition }) },
    createWorkflow: (data) => { calls.push(['create', data]); return response({ id: 99 }) },
    updateWorkflow: (id, data) => { calls.push(['update', id, data]); return response({}) },
    deployWorkflow: (id) => { calls.push(['deploy', id]); return response({}) },
    triggerWorkflow: (id) => { calls.push(['trigger', id]); return response({}) },
    getWorkflows: () => response([]),
    publishWorkflowVersion: (id, data) => { calls.push(['publish', id, data]); return response({ id: 1, versionNo: 1 }) },
    getWorkflowVersions: (id) => { calls.push(['versions', id]); return response([{ id: 1, versionNo: 1, status: 'ACTIVE' }]) },
    getWorkflowVersion: (id, versionId) => {
      calls.push(['version', id, versionId])
      return response({ id: versionId, versionNo: 2, definitionJson: definition, layoutJson: null })
    },
    deprecateWorkflowVersion: (id, versionId) => { calls.push(['deprecate', id, versionId]); return response({}) }
  }, () => true, {
    warning: message => warnings.push(message), success() {}, error() {}
  }, {
    confirm: () => { calls.push(['confirm']); return Promise.resolve() }
  },
  WORKFLOW_NODE_TYPES, VARIABLE_TYPES, workflowSupportError,
  buildWorkflowDefinition, buildWorkflowLayout, applyWorkflowLayout, extractLayoutFromNodes,
  parseWorkflowDefinition, inputsToBindings, bindingsToInputs, normalizeVariables,
  variablesToEditable, validateTemplateReferences, insertTemplateReference, buildReferenceSuggestions)
  return { editor, calls, warnings }
}

await check('PoC offers exactly four supported node types', () => {
  assert.deepEqual(WORKFLOW_NODE_TYPES.map(node => node.type), ['START', 'AGENT', 'CONDITION', 'END'])
  assert.equal(workflowSupportError({ nodes: WORKFLOW_NODE_TYPES }), '')
})

await check('legacy parallel/join graphs remain intact and are blocked', () => {
  const graph = { nodes: [{ id: 'fork', type: 'PARALLEL', config: { branches: 2 } }, { id: 'join', type: 'JOIN' }], edges: [{ source: 'fork', target: 'join' }] }
  const original = JSON.stringify(graph)
  assert.match(workflowSupportError(graph), /PARALLEL、JOIN/)
  assert.match(workflowSupportError(original), /禁止保存和运行/)
  assert.equal(JSON.stringify(graph), original)
})

await check('unreadable and unknown graph definitions fail closed', () => {
  for (const definition of ['{invalid', null, {}, { nodes: [null] }, { nodes: [{ type: 'CODE' }] }]) {
    assert.ok(workflowSupportError(definition))
  }
})

await check('actual canvas drop converts canvas bounds, pan and zoom through Vue Flow', () => {
  const harness = createCanvas()
  try {
    harness.flow.viewport.value = { x: 120, y: -40, zoom: 2 }
    harness.canvas.onDrop({
      clientX: 520, clientY: 400,
      dataTransfer: { getData: () => JSON.stringify({ id: '1', type: 'AGENT', label: '测试Agent' }) }
    })
    assert.equal(harness.props.nodes.length, 1)
    assert.deepEqual({ x: harness.props.nodes[0].x, y: harness.props.nodes[0].y }, { x: 90, y: 156 })
  } finally {
    harness.close()
  }
})

await check('drop preserves negative graph positions after panning', () => {
  const harness = createCanvas()
  try {
    harness.flow.viewport.value = { x: 120, y: -40, zoom: 2 }
    harness.canvas.onDrop({
      clientX: 120, clientY: 100,
      dataTransfer: { getData: () => JSON.stringify({ id: '1', type: 'START' }) }
    })
    assert.deepEqual({ x: harness.props.nodes[0].x, y: harness.props.nodes[0].y }, { x: -110, y: 6 })
  } finally {
    harness.close()
  }
})

await check('forged unsupported drops and duplicate ids cannot add nodes', () => {
  const harness = createCanvas()
  try {
    harness.props.nodes = [{ id: 'kept', type: 'PARALLEL', x: 10, y: 20 }]
    for (const payload of [{ id: '2', type: 'PARALLEL' }, { id: '3', type: 'JOIN' }, { id: 'kept', type: 'AGENT' }, null]) {
      harness.canvas.onDrop({ clientX: 100, clientY: 100, dataTransfer: { getData: () => JSON.stringify(payload) } })
    }
    assert.equal(harness.props.nodes.length, 1)
    assert.equal(harness.props.nodes[0].type, 'PARALLEL')
  } finally {
    harness.close()
  }
})

await check('selection drag persists every changed node and retains node configuration', async () => {
  const harness = createCanvas()
  try {
    assert.ok(canvasSource.includes('@nodes-change="onNodesChange"'))
    harness.props.nodes = [
      { id: '1', type: 'AGENT', x: 10, y: 20, agentId: 12, paramsText: '{"prompt":"keep"}' },
      { id: '2', type: 'CONDITION', x: 100, y: 200, condition: { field: 'status', operator: 'EQ', value: 'OK' } }
    ]
    await nextTick()
    harness.flow.updateNodePositions([
      { id: '1', position: { x: 30, y: 40 } },
      { id: '2', position: { x: 120, y: 220 } }
    ], true, true)
    await nextTick()
    assert.deepEqual(harness.props.nodes.map(({ id, x, y }) => ({ id, x, y })), [
      { id: '1', x: 30, y: 40 }, { id: '2', x: 120, y: 220 }
    ])
    assert.equal(harness.props.nodes[0].agentId, 12)
    assert.equal(harness.props.nodes[0].paramsText, '{"prompt":"keep"}')
    assert.deepEqual(harness.props.nodes[1].condition, { field: 'status', operator: 'EQ', value: 'OK' })
    assert.deepEqual(harness.flow.nodes.value.map(node => node.position), [{ x: 30, y: 40 }, { x: 120, y: 220 }])
  } finally {
    harness.close()
  }
})

await check('keyboard position updates survive save and reload without a drag-stop event', async () => {
  const harness = createCanvas()
  try {
    harness.props.nodes = [{ id: '1', type: 'START', x: 10, y: 20 }]
    await nextTick()
    harness.flow.updateNodePositions([{ id: '1', position: { x: 15, y: 20 } }], true, false)
    await nextTick()
    harness.flow.updateNodePositions([{ id: '1', position: { x: 15, y: 25 } }], true, false)
    await nextTick()
    const restored = JSON.parse(JSON.stringify({ nodes: harness.props.nodes })).nodes[0]
    assert.deepEqual({ x: restored.x, y: restored.y }, { x: 15, y: 25 })
  } finally {
    harness.close()
  }
})

await check('selection, dimensions and invalid position changes leave stored positions alone', () => {
  const nodes = [{ id: '1', type: 'START', x: 10, y: 20 }]
  assert.equal(applyWorkflowPositionChanges(nodes, [
    { id: '1', type: 'select', selected: true },
    { id: '1', type: 'dimensions', dimensions: { width: 100, height: 40 } },
    { id: '1', type: 'position', dragging: false },
    { id: '1', type: 'position', position: { x: NaN, y: 40 } }
  ]), nodes)
})

await check('editor refuses to save legacy nodes without silently deleting them', async () => {
  const legacy = [{ id: 'legacy', type: 'PARALLEL', x: 10, y: 20 }, { id: 'join', type: 'JOIN' }]
  const harness = createEditor({ nodes: legacy })
  harness.editor.workflowNodes.value = legacy
  harness.editor.currentWorkflow.value = { workflowName: '旧编排', workflowCode: 'legacy' }
  const original = JSON.stringify(harness.editor.workflowNodes.value)
  await harness.editor.saveWorkflow()
  assert.deepEqual(harness.calls, [])
  assert.match(harness.warnings[0], /PARALLEL、JOIN/)
  assert.equal(JSON.stringify(harness.editor.workflowNodes.value), original)
})

await check('trigger checks latest server definition before confirmation, deploy or run', async () => {
  const harness = createEditor(JSON.stringify({ nodes: [{ id: 'legacy', type: 'PARALLEL' }] }))
  await harness.editor.triggerWorkflow({ id: 7, status: 1, workflowDefinition: JSON.stringify({ nodes: [{ id: 'start', type: 'START' }] }) })
  assert.deepEqual(harness.calls, [['get', 7]])
  assert.match(harness.warnings[0], /禁止保存和运行/)
})

await check('allowed graphs can still be confirmed, deployed and triggered', async () => {
  const harness = createEditor(JSON.stringify({ nodes: [{ id: 'start', type: 'START' }, { id: 'end', type: 'END' }] }))
  await harness.editor.triggerWorkflow({ id: 8, status: 1 })
  assert.deepEqual(harness.calls, [['get', 8], ['confirm'], ['deploy', 8], ['trigger', 8]])
  assert.deepEqual(harness.warnings, [])
})

await check('template references are extracted and validated against declared variables and graph nodes', () => {
  const value = {
    prompt: '写 {{variables.topic}} 用 {{nodes.1.outputs.result}} 和 {{inputs.style}}',
    extra: ['{{variables.missing}}', { nested: '{{nodes.nope.outputs.x}}' }]
  }
  assert.deepEqual(extractTemplateReferences(value), [
    'variables.topic', 'nodes.1.outputs.result', 'inputs.style', 'variables.missing', 'nodes.nope.outputs.x'
  ])
  const errors = validateTemplateReferences(value, {
    variableNames: ['topic'],
    nodeIds: ['1', '2']
  })
  assert.equal(errors.length, 2)
  assert.match(errors[0], /变量「missing」未/)
  assert.match(errors[1], /节点「nope」不在/)
  assert.deepEqual(validateTemplateReferences('{{variables.topic}}', { variableNames: ['topic'] }), [])
  assert.match(validateTemplateReferences('{{nodes.1.outputs}}', { nodeIds: ['1'] })[0], /格式应为/)
  assert.match(validateTemplateReferences('{{foo.bar}}')[0], /不支持的引用/)
})

await check('input bindings convert to definition inputs map and back', () => {
  const bindings = inputsToBindings({ prompt: '{{variables.topic}}', limit: '3' })
  assert.deepEqual(bindings, [
    { key: 'prompt', value: '{{variables.topic}}' },
    { key: 'limit', value: '3' }
  ])
  assert.deepEqual(bindingsToInputs([...bindings, { key: '  ', value: 'drop' }]), {
    prompt: '{{variables.topic}}',
    limit: '3'
  })
  assert.deepEqual(bindingsToInputs(undefined), {})
})

await check('multi-node I/O chain survives definition round-trip for execution', () => {
  const nodes = [
    { id: 'n1', type: 'START', label: 'start', x: 0, y: 0, paramsText: '{}', inputBindings: [] },
    {
      id: 'n2', type: 'AGENT', label: 'produce', x: 100, y: 0, agentId: 3,
      params: {}, paramsText: '{}',
      inputBindings: [{ key: 'topic', value: '{{inputs.topic}}' }],
      timeout: 30, retryCount: 0
    },
    {
      id: 'n3', type: 'AGENT', label: 'consume', x: 200, y: 0, agentId: 4,
      params: {}, paramsText: '{}',
      inputBindings: [
        { key: 'summary', value: '{{nodes.n2.outputs.draft}}' },
        { key: 'topic', value: '{{inputs.topic}}' }
      ],
      timeout: 30, retryCount: 0
    }
  ]
  const definition = buildWorkflowDefinition({
    nodes,
    edges: [
      { source: 'n1', target: 'n2' },
      { source: 'n2', target: 'n3' }
    ],
    variables: [{ name: 'topic', type: 'string', required: true, defaultValue: 'demo' }]
  })
  assert.equal(definition.nodes[2].inputs.summary, '{{nodes.n2.outputs.draft}}')
  assert.equal(definition.nodes[2].inputs.topic, '{{inputs.topic}}')
  assert.equal(definition.nodes[1].inputs.topic, '{{inputs.topic}}')

  const parsed = parseWorkflowDefinition(JSON.stringify(definition))
  assert.equal(parsed.nodes.find((n) => n.id === 'n3').inputs.summary, '{{nodes.n2.outputs.draft}}')

  const roundTrip = buildWorkflowDefinition({
    nodes: parsed.nodes.map((n) => ({
      id: n.id,
      type: n.type,
      label: n.label || n.id,
      x: 0,
      y: 0,
      agentId: n.agentId,
      params: n.params || {},
      paramsText: JSON.stringify(n.params || {}),
      inputBindings: inputsToBindings(n.inputs || {})
    })),
    edges: parsed.edges,
    variables: parsed.variables
  })
  assert.deepEqual(roundTrip.nodes.find((n) => n.id === 'n3').inputs, definition.nodes[2].inputs)
})

await check('variables normalize with type coercion and duplicate name dropping', () => {
  const normalized = normalizeVariables([
    { name: 'topic', type: 'string', required: true, defaultValueText: 'hello' },
    { name: 'topic', type: 'number' },
    { name: 'limit', type: 'number', required: true, defaultValueText: '5' },
    { name: 'flag', type: 'boolean', defaultValueText: 'true' },
    { name: 'cfg', type: 'object', defaultValueText: '{"a":1}' },
    { name: '   ', type: 'string' }
  ])
  assert.equal(normalized.length, 4)
  assert.deepEqual(normalized[0], { name: 'topic', type: 'string', required: true, defaultValue: 'hello' })
  assert.equal(normalized[1].defaultValue, 5)
  assert.equal(normalized[2].defaultValue, true)
  assert.deepEqual(normalized[3].defaultValue, { a: 1 })
  const editable = variablesToEditable(normalized)
  assert.equal(editable[1].defaultValueText, '5')
})

await check('definition keeps execution contract only and layout is split out', () => {
  const nodes = [
    { id: '1', type: 'START', label: '开始', x: 12, y: 34, paramsText: '{}', inputBindings: [] },
    {
      id: '2', type: 'AGENT', label: 'Agent', x: 100, y: 200, agentId: 3,
      params: { model: 'm' }, paramsText: '{"model":"m"}',
      inputBindings: [{ key: 'prompt', value: '{{variables.topic}}' }],
      timeout: 30, retryCount: 1
    }
  ]
  const definition = buildWorkflowDefinition({
    nodes,
    edges: [{ source: '1', target: '2' }],
    variables: [{ name: 'topic', type: 'string', required: true }]
  })
  assert.equal(definition.schemaVersion, WORKFLOW_SCHEMA_VERSION)
  assert.equal(definition.schemaVersion, 1)
  assert.deepEqual(definition.variables, [{ name: 'topic', type: 'string', required: true }])
  assert.equal(definition.nodes[1].inputs.prompt, '{{variables.topic}}')
  assert.equal(definition.nodes[1].x, undefined)
  assert.equal(definition.nodes[1].y, undefined)
  assert.equal(definition.nodes[1].paramsText, undefined)
  assert.equal(definition.nodes[1].inputBindings, undefined)
  const layout = buildWorkflowLayout(nodes, { x: 10, y: 20, zoom: 1.5 })
  assert.deepEqual(layout.nodes, [{ id: '1', x: 12, y: 34 }, { id: '2', x: 100, y: 200 }])
  assert.deepEqual(layout.viewport, { x: 10, y: 20, zoom: 1.5 })
  const restored = applyWorkflowLayout([{ id: '1', type: 'START' }, { id: '2', type: 'AGENT' }], layout)
  assert.deepEqual(restored.map(n => ({ id: n.id, x: n.x, y: n.y })), layout.nodes)
  const legacy = extractLayoutFromNodes([{ id: 'a', x: 5, y: 6 }])
  assert.deepEqual(legacy.nodes, [{ id: 'a', x: 5, y: 6 }])
})

await check('condition edges get true/false labels and parseWorkflowDefinition tolerates bad JSON', () => {
  const definition = buildWorkflowDefinition({
    nodes: [{ id: 'c', type: 'CONDITION', x: 0, y: 0 }, { id: 'a', type: 'END', x: 1, y: 1 }, { id: 'b', type: 'END', x: 2, y: 2 }],
    edges: [{ source: 'c', target: 'a' }, { source: 'c', target: 'b' }]
  })
  assert.equal(definition.edges[0].condition, 'true')
  assert.equal(definition.edges[1].condition, 'false')
  const parsed = parseWorkflowDefinition('{not json')
  assert.equal(parsed.schemaVersion, 1)
  assert.deepEqual(parsed.nodes, [])
  assert.deepEqual(parsed.variables, [])
  const roundTrip = parseWorkflowDefinition(JSON.stringify(definition))
  assert.equal(roundTrip.variables.length, 0)
  assert.equal(roundTrip.nodes.length, 3)
})

await check('reference suggestions prefer variables, then upstream node outputs, then run inputs', () => {
  const suggestions = buildReferenceSuggestions({
    variables: [{ name: 'topic' }],
    nodes: [{ id: '1' }, { id: '2' }, { id: '3' }],
    currentNodeId: '3',
    edges: [{ source: '1', target: '3' }]
  })
  assert.deepEqual(suggestions.map(item => item.reference), [
    '{{variables.topic}}',
    '{{nodes.1.outputs.output}}',
    '{{inputs.field}}'
  ])
  assert.equal(insertTemplateReference('你好 ', '{{variables.topic}}'), '你好 {{variables.topic}}')
  assert.equal(insertTemplateReference('abcd', 'X', 2), 'abXcd')
})

await check('editor save writes schemaVersion, variables and node inputs without layout', async () => {
  const harness = createEditor()
  harness.editor.workflowNodes.value = [
    { id: '1', type: 'START', x: 10, y: 20, inputBindings: [] },
    {
      id: '2', type: 'AGENT', x: 100, y: 200,
      paramsText: '{"prompt":"hi"}',
      inputBindings: [{ key: 'prompt', value: '{{variables.topic}}' }]
    }
  ]
  harness.editor.workflowEdges.value = [{ source: '1', target: '2' }]
  harness.editor.workflowVariables.value = [{ name: 'topic', type: 'string', required: true, defaultValueText: '' }]
  harness.editor.currentWorkflow.value = { id: 5, workflowName: '测试', workflowCode: 'WF1' }
  await harness.editor.saveWorkflow()
  const createCall = harness.calls.find(call => call[0] === 'create')
  assert.ok(createCall)
  const definition = JSON.parse(createCall[1].workflowDefinition)
  assert.equal(definition.schemaVersion, 1)
  assert.deepEqual(definition.variables, [{ name: 'topic', type: 'string', required: true }])
  assert.deepEqual(definition.nodes[1].inputs, { prompt: '{{variables.topic}}' })
  assert.equal(definition.nodes[1].x, undefined)
  assert.equal(definition.nodes[1].y, undefined)
  assert.deepEqual(harness.warnings, [])
})

await check('editor blocks save when a variable reference is undeclared', async () => {
  const harness = createEditor()
  harness.editor.workflowNodes.value = [
    {
      id: '1', type: 'AGENT', x: 0, y: 0,
      paramsText: '{}',
      inputBindings: [{ key: 'prompt', value: '{{variables.nope}}' }]
    }
  ]
  harness.editor.workflowVariables.value = []
  harness.editor.currentWorkflow.value = { id: 5, workflowName: '测试', workflowCode: 'WF1' }
  await harness.editor.saveWorkflow()
  assert.deepEqual(harness.calls, [])
  assert.match(harness.warnings[0], /nope/)
})

await check('editor blocks save when a node output reference points outside the graph', async () => {
  const harness = createEditor()
  harness.editor.workflowNodes.value = [
    { id: '1', type: 'START', x: 0, y: 0, inputBindings: [] },
    {
      id: '2', type: 'AGENT', x: 0, y: 0,
      paramsText: '{}',
      inputBindings: [{ key: 'prompt', value: '{{nodes.ghost.outputs.text}}' }]
    }
  ]
  harness.editor.currentWorkflow.value = { id: 5, workflowName: '测试', workflowCode: 'WF1' }
  const errors = harness.editor.collectReferenceErrors()
  assert.equal(errors.length, 1)
  assert.match(errors[0], /ghost/)
  await harness.editor.saveWorkflow()
  assert.deepEqual(harness.calls, [])
  assert.match(harness.warnings[0], /ghost/)
})

await check('publish version sends definitionJson and layoutJson without node coordinates in definition', async () => {
  const harness = createEditor()
  harness.editor.workflowNodes.value = [
    { id: '1', type: 'START', x: 15, y: 25, inputBindings: [] },
    { id: '2', type: 'END', x: 80, y: 90, inputBindings: [] }
  ]
  harness.editor.workflowEdges.value = [{ source: '1', target: '2' }]
  harness.editor.workflowVariables.value = [{ name: 'topic', type: 'string', required: false, defaultValueText: 'x' }]
  harness.editor.currentWorkflow.value = { id: 7, workflowName: '发布', workflowCode: 'WF7' }
  await harness.editor.publishVersion()
  const publish = harness.calls.find(call => call[0] === 'publish')
  assert.ok(publish)
  assert.equal(publish[1], 7)
  const definition = JSON.parse(publish[2].definitionJson)
  const layout = JSON.parse(publish[2].layoutJson)
  assert.equal(definition.schemaVersion, 1)
  assert.deepEqual(definition.variables, [{ name: 'topic', type: 'string', required: false, defaultValue: 'x' }])
  assert.equal(definition.nodes[0].x, undefined)
  assert.deepEqual(layout.nodes, [{ id: '1', x: 15, y: 25 }, { id: '2', x: 80, y: 90 }])
  assert.ok(harness.calls.some(call => call[0] === 'versions'))
})

await check('binding helpers on the editor node panel add, insert and drop rows', async () => {
  const harness = createEditor()
  const node = reactive({ id: '1', type: 'AGENT', x: 0, y: 0, inputBindings: [] })
  harness.editor.workflowNodes.value = [node]
  harness.editor.workflowVariables.value = [{ name: 'topic', type: 'string', required: true, defaultValueText: '' }]
  harness.editor.selectedNode.value = node
  harness.editor.addBinding()
  assert.equal(node.inputBindings.length, 1)
  node.inputBindings[0].key = 'prompt'
  harness.editor.insertRefIntoBinding(node.inputBindings[0], '{{variables.topic}}')
  assert.equal(node.inputBindings[0].value, '{{variables.topic}}')
  await nextTick()
  assert.deepEqual(harness.editor.selectedNodeBindingErrors.value, [])
  node.inputBindings[0].value = '{{variables.ghost}}'
  await nextTick()
  assert.equal(harness.editor.selectedNodeBindingErrors.value.length, 1)
  harness.editor.removeBinding(0)
  assert.equal(node.inputBindings.length, 0)
})

console.log(`Workflow canvas regression tests passed (${passed} checks)`)
