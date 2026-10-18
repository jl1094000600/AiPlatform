import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { computed, createRenderer, defineComponent, nextTick, reactive, ref, watch } from 'vue'
import { useVueFlow } from '@vue-flow/core'
import {
  WORKFLOW_NODE_TYPES, workflowSupportError, createDroppedWorkflowNode, applyWorkflowPositionChanges
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
const canvasScript = canvasSource.split('<script setup>')[1].split('</script>')[0]
  .split('\n').filter(line => !line.startsWith('import ')).join('\n')
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
const editorScript = editorSource.split('<script setup>')[1].split('</script>')[0]
  .split('\n').filter(line => !line.startsWith('import ')).join('\n')
const setupEditor = new Function(
  'computed', 'ref', 'onMounted', 'nextTick', 'api', 'hasPermission', 'ElMessage', 'ElMessageBox', 'WORKFLOW_NODE_TYPES', 'workflowSupportError',
  `${editorScript}\nreturn { workflowNodes, currentWorkflow, workflowSupportMessage, saveWorkflow, triggerWorkflow };`
)
const createEditor = (definition) => {
  const calls = []
  const warnings = []
  const response = (data) => Promise.resolve({ data: { code: 200, data } })
  const editor = setupEditor(computed, ref, () => {}, nextTick, {
    getWorkflow: (id) => { calls.push(['get', id]); return response({ id, status: 0, workflowDefinition: definition }) },
    createWorkflow: () => { calls.push(['create']); return response({}) },
    updateWorkflow: () => { calls.push(['update']); return response({}) },
    deployWorkflow: (id) => { calls.push(['deploy', id]); return response({}) },
    triggerWorkflow: (id) => { calls.push(['trigger', id]); return response({}) },
    getWorkflows: () => response([])
  }, () => true, {
    warning: message => warnings.push(message), success() {}, error() {}
  }, {
    confirm: () => { calls.push(['confirm']); return Promise.resolve() }
  }, WORKFLOW_NODE_TYPES, workflowSupportError)
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

console.log(`Workflow canvas regression tests passed (${passed} checks)`)
