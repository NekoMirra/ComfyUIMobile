const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const root = path.join(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

const panelSource = read('app/src/main/java/com/local/comfyuimobile/bridge/NodePanelBridge.kt');
const policySource = read('app/src/main/java/com/local/comfyuimobile/bridge/NodeModePolicy.kt');
const modelSource = read('app/src/main/java/com/local/comfyuimobile/model/Models.kt');
const manifestSource = read('app/src/main/java/com/local/comfyuimobile/bridge/ComfyBridge.kt');

/** 取出 Kotlin 里的 raw string 常量。 */
function extractConst(name) {
  const match = panelSource.match(new RegExp('private const val ' + name + '\\s*=\\s*"""\\r?\\n([\\s\\S]*?)"""'));
  assert.ok(match, '缺少常量 ' + name);
  return match[1];
}

/** 取出 Kotlin 函数里返回的脚本模板。 */
function extractTemplate(functionName) {
  const match = panelSource.match(
    new RegExp('fun ' + functionName + '\\([\\s\\S]*?return """\\r?\\n([\\s\\S]*?)"""\\s*\\.trimIndent\\(\\)'),
  );
  assert.ok(match, '缺少脚本模板 ' + functionName);
  return match[1];
}

/** 按 Kotlin 里的插值方式拼出最终脚本。 */
function buildScript(templateName, values) {
  let script = extractTemplate(templateName);
  script = script.replace(/\$APP_LOOKUP_JS/g, () => extractConst('APP_LOOKUP_JS'));
  script = script.replace(/\$FIND_NODE_JS/g, () => extractConst('FIND_NODE_JS'));
  script = script.replace(/\$COLLECT_VALUES_JS/g, () => extractConst('COLLECT_VALUES_JS'));
  script = script.replace(/\$FOCUS_NODE_JS/g, () => extractConst('FOCUS_NODE_JS'));
  script = script.replace(/\$idLiteral/g, () => JSON.stringify(values.nodeId));
  script = script.replace(/\$widgetLiteral/g, () => (values.widgetName === undefined ? 'null' : JSON.stringify(values.widgetName)));
  script = script.replace(/\$valuesLiteral/g, () => JSON.stringify(values.initialValues || {}));
  return script;
}

/** 造一个够用的官方前端环境。 */
function makeContext(state) {
  const widgetText = { name: 'pose', type: 'text', value: 'old.png' };
  const widgetButton = {
    name: '打开图库',
    type: 'button',
    value: null,
    callback: () => { state.triggered.push('打开图库'); },
  };
  const widgetDom = { name: 'domWidget', element: {}, value: 'dom-value' };
  const node = {
    id: 7,
    type: 'PoseGallery',
    pos: [10, 20],
    size: [200, 100],
    widgets: [widgetText, widgetButton, widgetDom],
  };
  const app = {
    graph: {
      _nodes: [node],
      getNodeById: (id) => (node.id === Number(id) ? node : null),
    },
    canvas: {
      ds: { scale: 1, offset: [0, 0] },
      centerOnNode: (target) => { state.centered = target.id; },
      setDirty: () => { state.dirty += 1; },
      draw: () => { state.drawn += 1; },
    },
  };
  const context = {
    window: {
      comfyAPI: { app: { app } },
      LiteGraph: { registered_node_types: { PoseGallery: 1 } },
    },
    document: { querySelector: () => null },
    setTimeout,
    JSON,
    Object,
    Array,
    Math,
    Number,
    String,
    Boolean,
    console,
  };
  return { context, node, widgetText, widgetButton, widgetDom };
}

async function runScript(context, script) {
  const raw = await vm.runInNewContext(script, context);
  return JSON.parse(raw);
}

/** 按 ComfyBridge 里的 buildNodeModeScript 拼出注入脚本。 */
function buildNodeModeScript(modes) {
  const match = manifestSource.match(
    /internal fun buildNodeModeScript\([\s\S]*?return """\r?\n([\s\S]*?)"""\s*\.trimIndent\(\)/,
  );
  assert.ok(match, '缺少 buildNodeModeScript');
  const encoded = Buffer.from(JSON.stringify(modes), 'utf8').toString('base64');
  return match[1].replace(/\$encoded/g, () => encoded);
}

/** 在 VM 里跑一遍生产用的清单脚本，确认新增的坐标、尺寸与连线真的被产出。 */
async function runManifestScript() {
  const support = read('app/src/main/assets/seed_support.js');
  const match = manifestSource.match(/private fun workflowManifestScript\([\s\S]*?\) = """([\s\S]*?)"""\.trimIndent\(\)/);
  assert.ok(match, '缺少 workflowManifestScript');
  const workflow = {
    nodes: [
      { id: 1, type: 'Seed (rgthree)', widgets_values: [-1] },
      { id: 2, type: 'SaveImage' },
    ],
    links: [[10, 1, 0, 2, 0, 'INT']],
  };
  const persisted = { path: 'workflows/test.json', isLoaded: true, activeState: workflow };
  const store = {
    activeWorkflow: persisted,
    openWorkflows: [persisted],
    workflows: [persisted],
    openWorkflowsInBackground() {},
    getWorkflowByPath: () => persisted,
    syncWorkflows: async () => {},
  };
  const seed = { name: 'seed', type: 'number', value: -1, options: {}, callback() {} };
  const button = { type: 'button', name: 'Randomize', callback() {} };
  const node = {
    id: 1,
    type: 'Seed (rgthree)',
    pos: [120, -40],
    size: [260, 120],
    widgets: [seed, button],
    outputs: [{ type: 'INT' }],
  };
  const output = {
    id: 2,
    type: 'SaveImage',
    pos: [640, 80],
    size: [300, 140],
    widgets: [],
    inputs: [{ name: 'images', type: 'INT', link: 10 }],
  };
  const graph = {
    _nodes: [node, output],
    links: new Map([[10, { origin_id: 1, origin_slot: 0, target_id: 2, target_slot: 0, type: 'INT' }]]),
  };
  const app = { graph, extensionManager: { workflow: store }, loadGraphData: async () => {} };
  const context = {
    window: { __comfyMobileApp: app },
    TextDecoder,
    Uint8Array,
    Map,
    Set,
    structuredClone,
    atob: (value) => Buffer.from(value, 'base64').toString('binary'),
    setTimeout: (fn) => fn(),
    JSON,
    Object,
    Array,
    Number,
    String,
    Math,
    console,
  };
  const script = match[1]
    .replace('$encodedWorkflow', Buffer.from(JSON.stringify(workflow)).toString('base64'))
    .replace('$seedSupportScript', support)
    .replace(/\$\{encodedWorkflowPath\?\.let\s*\{[^\n]*\}[^\n]*\}/, "'workflows/test.json'")
    .replaceAll('$inspectCurrentGraph', 'false')
    .replaceAll('$nativeWorkflowOpen', 'false');
  return JSON.parse(await vm.runInNewContext(script, context));
}

async function main() {
  const state = { triggered: [], dirty: 0, drawn: 0, centered: null };
  const { context, node, widgetText, widgetButton, widgetDom } = makeContext(state);

  // 1. 打开面板脚本：定位节点、写入 widget、跳过 button 与 DOM widget、触发按钮。
  const openScript = buildScript('buildOpenPanelScript', {
    nodeId: '7',
    widgetName: '打开图库',
    initialValues: { pose: 'new.png', domWidget: 'should-not-be-written' },
  });
  assert.ok(openScript.includes('getNodeById'), '打开脚本必须定位节点');
  assert.ok(openScript.includes('widget.value = value'), '打开脚本必须写入 widget');
  assert.ok(openScript.includes("widget.type === 'button'"), '打开脚本必须跳过 button');
  assert.ok(openScript.includes('widget.element'), '打开脚本必须跳过 DOM widget');

  const opened = await runScript(context, openScript);
  assert.equal(opened.ok, true, opened.error);
  assert.equal(widgetText.value, 'new.png', '未保存的编辑值必须写进 widget');
  assert.equal(widgetDom.value, 'dom-value', 'DOM widget 不能被写入');
  assert.equal(widgetButton.value, null, 'button widget 不能被写入');
  assert.deepEqual(state.triggered, ['打开图库'], '必须触发面板按钮');
  assert.equal(state.centered, 7, '必须把画布移到目标节点');
  assert.ok(state.dirty > 0 && state.drawn > 0, '必须重绘画布');

  // 2. 读回脚本：跳过 button 与 DOM widget，并给出真正改动过的参数。
  const readScript = buildScript('buildReadValuesScript', { nodeId: '7' });
  assert.ok(readScript.includes("widget.type === 'button'"), '读回脚本必须跳过 button');
  assert.ok(readScript.includes('widget.element'), '读回脚本必须跳过 DOM widget');

  const readBefore = await runScript(context, readScript);
  assert.equal(readBefore.ok, true, readBefore.error);
  assert.deepEqual(Object.keys(readBefore.changed), [], '没有改动时不应报告任何参数');

  widgetText.value = 'panel-picked.png';
  const readAfter = await runScript(context, readScript);
  assert.equal(readAfter.ok, true, readAfter.error);
  assert.equal(readAfter.changed.pose, 'panel-picked.png', '必须报告面板改动过的参数');
  assert.equal(readAfter.changed.domWidget, undefined, 'DOM widget 不能出现在读回结果里');
  assert.equal(readAfter.changed['打开图库'], undefined, 'button 不能出现在读回结果里');

  // 3. 定位脚本：只移动画布。
  const focusScript = buildScript('buildFocusNodeScript', { nodeId: '7' });
  const focused = await runScript(context, focusScript);
  assert.equal(focused.ok, true, focused.error);
  assert.equal(focused.focused, true, '定位脚本必须报告成功');

  // 4. 出错时必须显式返回错误，而不是静默成功。
  const missingScript = buildScript('buildOpenPanelScript', { nodeId: '404', widgetName: null, initialValues: {} });
  const missing = await runScript(context, missingScript);
  assert.equal(missing.ok, false, '找不到节点时必须返回失败');
  assert.ok(String(missing.error).includes('找不到节点'), '失败必须带上错误信息');

  // 5. 就绪探针：两个条件缺一不可。
  const probeSource = extractConst('READY_PROBE_JS');
  assert.ok(probeSource.includes('window.comfyAPI.app.app.graph'), '探针必须检查 graph');
  assert.ok(probeSource.includes('registered_node_types'), '探针必须检查节点注册表');
  const probeContext = {
    window: { comfyAPI: { app: { app: { graph: {} } } }, LiteGraph: { registered_node_types: { A: 1 } } },
    String,
    Object,
  };
  assert.equal(await vm.runInNewContext(probeSource, probeContext), '1');
  const probeWithoutTypes = {
    window: { comfyAPI: { app: { app: { graph: {} } } }, LiteGraph: { registered_node_types: {} } },
    String,
    Object,
  };
  assert.equal(await vm.runInNewContext(probeSource, probeWithoutTypes), '0');
  const probeWithoutGraph = {
    window: { comfyAPI: { app: { app: {} } }, LiteGraph: { registered_node_types: { A: 1 } } },
    String,
    Object,
  };
  assert.equal(await vm.runInNewContext(probeSource, probeWithoutGraph), '0');

  // 6. 节点模式：按同一套规则在 node 里复刻一遍，与 Kotlin 的实现交叉验证。
  //    mode 是 LiteGraph 节点模式（0 始终执行 / 2 静音 / 4 绕过），落在工作流 JSON
  //    的 nodes[].mode 上；语义由官方前端构图时解析，服务端不读 prompt 里的 mode。
  const workflow = {
    nodes: [
      { id: 1, type: 'CheckpointLoaderSimple', mode: 0 },
      { id: '2', type: 'LoraLoader', mode: 0 },
    ],
    links: [[10, 1, 0, 2, 0, 'MODEL']],
  };
  const applyModesToWorkflow = (workflowJson, modes) => {
    const parsed = JSON.parse(workflowJson);
    const byId = new Map();
    (parsed.nodes || []).forEach((item) => byId.set(String(item.id), item));
    const missing = [];
    Object.entries(modes).forEach(([id, mode]) => {
      const target = byId.get(String(id));
      if (!target) { missing.push(id); return; }
      target.mode = mode;
    });
    if (missing.length) throw new Error('工作流里找不到节点：' + missing.join(', '));
    return parsed;
  };
  const collectModes = (workflowJson) => {
    const parsed = JSON.parse(workflowJson);
    const modes = {};
    (parsed.nodes || []).forEach((item) => { modes[String(item.id)] = item.mode === undefined ? 0 : item.mode; });
    return modes;
  };
  const applied = applyModesToWorkflow(JSON.stringify(workflow), { 1: 0, 2: 4 });
  assert.equal(applied.nodes[0].mode, 0, 'ALWAYS 必须写 0');
  assert.equal(applied.nodes[1].mode, 4, 'BYPASS 必须写 4');
  assert.equal(applied.nodes[1].type, 'LoraLoader', '其它字段不能被改写');
  assert.equal(applied.links[0][0], 10, '连线不能被改写');
  assert.deepEqual(collectModes(JSON.stringify(applied)), { 1: 0, 2: 4 });
  assert.deepEqual(collectModes(JSON.stringify({ nodes: [{ id: 5 }] })), { 5: 0 });
  assert.throws(() => applyModesToWorkflow(JSON.stringify(workflow), { 99: 2 }), /找不到节点/);

  // 7. Kotlin 侧必须与上面这套规则一致。
  assert.ok(modelSource.includes('ALWAYS(0'), 'NodeMode.ALWAYS 的 wire 值必须是 0');
  assert.ok(modelSource.includes('MUTE(2'), 'NodeMode.MUTE 的 wire 值必须是 2');
  assert.ok(modelSource.includes('BYPASS(4'), 'NodeMode.BYPASS 的 wire 值必须是 4');
  assert.ok(policySource.includes('applyToWorkflowJson'), 'NodeModePolicy 必须提供工作流 JSON 写入');
  assert.ok(policySource.includes('collectModesFromWorkflowJson'), 'NodeModePolicy 必须提供工作流 JSON 读回');
  assert.ok(policySource.includes('node.put("mode"'), 'NodeModePolicy 必须写 nodes[].mode');
  assert.ok(!policySource.includes('applyToPrompt'), 'prompt 层不再写 mode：服务端不消费该字段');
  assert.ok(!policySource.includes('collectBypassedNodes'), 'prompt 层不再读 mode');
  assert.ok(manifestSource.includes('buildNodeModeScript'), '桥接层必须提供画布模式脚本');
  assert.ok(manifestSource.includes('node.mode = Number(payload[id])'), '画布脚本必须设置 node.mode');

  // 8. 画布模式脚本：真的把 mode 写到节点上，找不到节点时明确报错。
  const modeScript = buildNodeModeScript({ 7: 4 });
  const modeContext = {
    window: { __comfyMobileApp: context.window.comfyAPI.app.app },
    TextDecoder,
    Uint8Array,
    atob: (value) => Buffer.from(value, 'base64').toString('binary'),
    JSON,
    Object,
    Array,
    Number,
    String,
    Math,
    console,
  };
  const modeResult = await runScript(modeContext, modeScript);
  assert.equal(modeResult.ok, true, modeResult.error);
  assert.equal(node.mode, 4, '脚本必须把 mode 写到画布节点上');
  assert.deepEqual(modeResult.applied, ['7']);
  const missingModeScript = buildNodeModeScript({ 99: 2 });
  const missingMode = await runScript(modeContext, missingModeScript);
  assert.equal(missingMode.ok, false, '找不到节点时必须明确失败');
  assert.ok(String(missingMode.error).includes('找不到节点'), '失败必须带上错误信息');

  // 9. 清单脚本必须带坐标、尺寸与连线。
  assert.ok(manifestSource.includes('posX: readNumber'), '清单脚本必须返回节点坐标');
  assert.ok(manifestSource.includes('sizeY: readNumber'), '清单脚本必须返回节点尺寸');
  assert.ok(manifestSource.includes('const links = collectGraphLinks();'), '清单脚本必须收集连线');
  assert.ok(manifestSource.includes('originId: String(originId)'), '连线必须带 originId');
  assert.ok(manifestSource.includes('targetSlot: Number(targetSlot) || 0'), '连线必须带 targetSlot');

  // 10. 真跑一遍清单脚本，确认坐标、尺寸与连线真的被产出。
  const manifest = await runManifestScript();
  assert.equal(manifest.ok, true, manifest.error);
  const seedNode = manifest.nodes.find((item) => item.id === '1');
  assert.ok(seedNode, '清单里必须有 Seed 节点');
  assert.equal(seedNode.posX, 120, '节点横坐标必须来自前端 pos');
  assert.equal(seedNode.posY, -40, '节点纵坐标必须来自前端 pos');
  assert.equal(seedNode.sizeX, 260, '节点宽度必须来自前端 size');
  assert.equal(seedNode.sizeY, 120, '节点高度必须来自前端 size');
  assert.equal(manifest.links.length, 1, '清单必须返回连线');
  assert.equal(manifest.links[0].linkId, '10');
  assert.equal(manifest.links[0].originId, '1');
  assert.equal(manifest.links[0].originSlot, 0);
  assert.equal(manifest.links[0].targetId, '2');
  assert.equal(manifest.links[0].targetSlot, 0);

  console.log('PASS: node panel scripts, node mode rules (workflow JSON + canvas), manifest graph fields');
}

main().catch((error) => {
  console.error('FAIL:', error);
  process.exitCode = 1;
});
