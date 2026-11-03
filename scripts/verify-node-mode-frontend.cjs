const { spawn } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const http = require('node:http');

/**
 * 用真实的 ComfyUI 前端核验节点模式（mode）语义（可选核验，不是离线桥接回归）。
 *
 * 关键事实：mode 是 LiteGraph 的节点模式，语义由官方前端在构图（graphToPrompt）
 * 时解析——被静音（2）或绕过（4）的节点不会出现在 api prompt 里；被绕过的节点
 * 下游会直接接到该节点的上游输出。服务端只消费 prompt 的 class_type 与 inputs，
 * 不读取 prompt 里的 mode 字段，所以 App 侧必须改工作流 JSON 的 nodes[].mode，
 * 再让官方前端重新构图。
 *
 * 用法：node scripts/verify-node-mode-frontend.cjs [serverUrl]
 * 退出码：0 = 核验通过；1 = 核验失败；2 = 环境不具备（没有服务器或浏览器），无法核验。
 *
 * 文件名刻意不叫 test-*.cjs：dev.ps1 会把 scripts/test-*.cjs 全部当作离线桥接回归
 * 执行，而本脚本需要一台正在运行的 ComfyUI 服务器。
 */

const SERVER_URL = process.argv[2] || 'http://127.0.0.1:8188';
const DEBUG_PORT = Number(process.env.COMFY_CDP_PORT || 9333);
const CHROME_CANDIDATES = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
];

function httpGetJson(url) {
  return new Promise((resolve, reject) => {
    const request = http.get(url, (response) => {
      let body = '';
      response.on('data', (chunk) => { body += chunk; });
      response.on('end', () => {
        try { resolve(JSON.parse(body)); } catch (error) { reject(error); }
      });
    });
    request.on('error', reject);
    request.setTimeout(5000, () => request.destroy(new Error('timeout ' + url)));
  });
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * ComfyUI 的官方前端在启动时会重定向并重建执行上下文，此时正在进行的
 * Runtime.evaluate 会以 "Execution context was destroyed" 失败。这是浏览器导航的竞态，
 * 不是被测语义的失败，所以只对这类瞬时错误重试，断言本身不变。
 */
const TRANSIENT_CDP_ERRORS = [
  'Execution context was destroyed',
  'Cannot find context with specified id',
  'Inspected target navigated or closed',
  'Target closed',
  'Session with given id not found',
];

function isTransientCdpError(error) {
  const message = String((error && error.message) || error || '');
  return TRANSIENT_CDP_ERRORS.some((needle) => message.includes(needle));
}

async function waitForTarget(port, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const targets = await httpGetJson(`http://127.0.0.1:${port}/json/list`);
      const page = targets.find((item) => item.type === 'page' && item.webSocketDebuggerUrl);
      if (page) return page;
    } catch (_) { /* 浏览器还没起来 */ }
    await sleep(300);
  }
  throw new Error('Chrome 调试端口没有就绪');
}

class CdpClient {
  constructor(socket) {
    this.socket = socket;
    this.nextId = 0;
    this.pending = new Map();
    socket.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (!message.id || !this.pending.has(message.id)) return;
      const { resolve, reject } = this.pending.get(message.id);
      this.pending.delete(message.id);
      if (message.error) reject(new Error(JSON.stringify(message.error)));
      else resolve(message.result);
    };
  }

  send(method, params = {}) {
    const id = ++this.nextId;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.socket.send(JSON.stringify({ id, method, params }));
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id);
          reject(new Error(`${method} 超时`));
        }
      }, 120000);
    });
  }

  async evaluate(expression) {
    const result = await this.send('Runtime.evaluate', {
      expression,
      awaitPromise: true,
      returnByValue: true,
    });
    if (result.exceptionDetails) {
      throw new Error('页面脚本异常：' + JSON.stringify(result.exceptionDetails.exception || result.exceptionDetails));
    }
    return result.result.value;
  }
}

/** 页面世界里的脚本：建图、切换 mode、调用官方 graphToPrompt()。 */
const PAGE_SCRIPT = String.raw`
(async () => {
  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const deadline = Date.now() + 120000;
  let app = null;
  let liteGraph = null;
  while (Date.now() < deadline) {
    app = (window.comfyAPI && window.comfyAPI.app && window.comfyAPI.app.app) || null;
    liteGraph = window.LiteGraph || null;
    const registered = liteGraph && liteGraph.registered_node_types
      ? Object.keys(liteGraph.registered_node_types).length > 0
      : false;
    if (app && liteGraph && registered && (app.graph || app.rootGraph)) break;
    await sleep(500);
  }
  if (!app || !liteGraph) return JSON.stringify({ ok: false, error: '官方前端未就绪' });
  const graph = app.graph || app.rootGraph;

  // 前端启动后会异步载入默认工作流，先等它稳定下来，避免中途把我们的图换掉。
  let lastCount = -1;
  let stable = 0;
  const settleDeadline = Date.now() + 60000;
  while (Date.now() < settleDeadline) {
    const count = (graph._nodes || []).length;
    if (count > 0 && count === lastCount) {
      stable += 1;
      if (stable >= 8) break;
    } else {
      stable = 0;
    }
    lastCount = count;
    await sleep(500);
  }
  await sleep(2000);

  const add = (type, pos) => {
    const node = liteGraph.createNode(type);
    node.pos = pos;
    graph.add(node);
    return node;
  };
  const outSlot = (node, type) => node.outputs.findIndex((slot) => slot.type === type);
  const inSlot = (node, name) => node.inputs.findIndex((slot) => slot.name === name);

  const build = () => {
    graph.clear();
    const loader = add('CheckpointLoaderSimple', [40, 40]);
    const clipEncode = add('CLIPTextEncode', [380, 40]);
    const lora = add('LoraLoader', [720, 40]);
    const empty = add('EmptyLatentImage', [40, 340]);
    const upscale = add('LatentUpscale', [380, 340]);
    const sampler = add('KSampler', [1060, 40]);
    const vae = add('VAEDecode', [1440, 40]);
    loader.connect(outSlot(loader, 'MODEL'), lora, inSlot(lora, 'model'));
    loader.connect(outSlot(loader, 'CLIP'), clipEncode, inSlot(clipEncode, 'clip'));
    loader.connect(outSlot(loader, 'VAE'), vae, inSlot(vae, 'vae'));
    clipEncode.connect(outSlot(clipEncode, 'CONDITIONING'), sampler, inSlot(sampler, 'positive'));
    lora.connect(outSlot(lora, 'MODEL'), sampler, inSlot(sampler, 'model'));
    empty.connect(outSlot(empty, 'LATENT'), upscale, inSlot(upscale, 'samples'));
    upscale.connect(outSlot(upscale, 'LATENT'), sampler, inSlot(sampler, 'latent_image'));
    sampler.connect(outSlot(sampler, 'LATENT'), vae, inSlot(vae, 'samples'));
    graph.setDirtyCanvas(true, true);
    return { loader, clipEncode, lora, empty, upscale, sampler, vae };
  };

  const runScenario = async (loraMode, upscaleMode) => {
    for (let attempt = 0; attempt < 6; attempt += 1) {
      const nodes = build();
      await sleep(200);
      const ids = {
        loader: String(nodes.loader.id),
        clipEncode: String(nodes.clipEncode.id),
        lora: String(nodes.lora.id),
        empty: String(nodes.empty.id),
        upscale: String(nodes.upscale.id),
        sampler: String(nodes.sampler.id),
        vae: String(nodes.vae.id),
      };
      const stillThere = (graph._nodes || []).some((node) => String(node.id) === ids.sampler);
      if (!stillThere) { await sleep(600); continue; }
      nodes.lora.mode = loraMode;
      nodes.upscale.mode = upscaleMode;
      nodes.lora.setDirtyCanvas?.(true, true);
      nodes.upscale.setDirtyCanvas?.(true, true);
      graph.setDirtyCanvas(true, true);
      await sleep(200);
      const result = await app.graphToPrompt();
      const output = result.output || {};
      if (!Object.prototype.hasOwnProperty.call(output, ids.sampler)) {
        await sleep(600);
        continue;
      }
      const samplerInput = output[ids.sampler] ? output[ids.sampler].inputs : null;
      const serialized = graph.serialize ? graph.serialize() : null;
      const serializedModes = {};
      ((serialized && serialized.nodes) || []).forEach((item) => { serializedModes[String(item.id)] = item.mode; });
      return {
        loraMode,
        upscaleMode,
        ids,
        promptNodeIds: Object.keys(output),
        promptNodeCount: Object.keys(output).length,
        loraInPrompt: Object.prototype.hasOwnProperty.call(output, ids.lora),
        upscaleInPrompt: Object.prototype.hasOwnProperty.call(output, ids.upscale),
        samplerModel: samplerInput ? samplerInput.model : null,
        samplerLatent: samplerInput ? samplerInput.latent_image : null,
        serializedModes,
        prompt: output,
      };
    }
    return { loraMode, upscaleMode, failed: true };
  };

  const scenarios = [];
  scenarios.push(await runScenario(0, 0));
  scenarios.push(await runScenario(4, 0));
  scenarios.push(await runScenario(2, 0));
  scenarios.push(await runScenario(0, 4));
  scenarios.push(await runScenario(0, 2));

  const failed = scenarios.filter((item) => item.failed);
  if (failed.length) return JSON.stringify({ ok: false, error: '无法稳定构建测试图：' + failed.length + ' 个场景' });
  return JSON.stringify({ ok: true, scenarios });
})()
`;

async function main() {
  const chromePath = CHROME_CANDIDATES.find((candidate) => fs.existsSync(candidate));
  if (!chromePath) {
    console.error('SKIP: 本机没有可用的 Chrome / Edge，无法做真实前端核验');
    process.exitCode = 2;
    return;
  }
  try {
    await httpGetJson(`${SERVER_URL}/api/system_stats`);
  } catch (error) {
    console.error(`SKIP: ComfyUI 服务器 ${SERVER_URL} 不可访问（${error.message}），无法做真实前端核验`);
    process.exitCode = 2;
    return;
  }

  const profileDir = fs.mkdtempSync(path.join(os.tmpdir(), 'comfy-node-mode-'));
  const browser = spawn(chromePath, [
    '--headless=new',
    `--remote-debugging-port=${DEBUG_PORT}`,
    `--user-data-dir=${profileDir}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-gpu',
    '--window-size=1600,1000',
    SERVER_URL,
  ], { stdio: 'ignore' });

  let client = null;
  try {
    const target = await waitForTarget(DEBUG_PORT, 30000);
    const socket = new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      socket.onopen = resolve;
      socket.onerror = () => reject(new Error('CDP 连接失败'));
    });
    client = new CdpClient(socket);
    await client.send('Runtime.enable');
    // 官方前端启动时会重定向页面并重建执行上下文，第一次 evaluate 可能正好撞上导航。
    // 只对这类瞬时 CDP 错误重试：等待页面稳定后在新上下文里重新执行同一段脚本。
    let raw = null;
    let lastError = null;
    for (let attempt = 1; attempt <= 4; attempt += 1) {
      try {
        raw = await client.evaluate(PAGE_SCRIPT);
        lastError = null;
        break;
      } catch (error) {
        lastError = error;
        if (!isTransientCdpError(error) || attempt === 4) break;
        console.error(`（第 ${attempt} 次执行撞上页面导航，稍后重试：${error.message}）`);
        await sleep(2000);
      }
    }
    if (lastError) throw lastError;
    const result = JSON.parse(raw);
    if (!result.ok) {
      console.error('FAIL: ' + result.error);
      process.exitCode = 1;
      return;
    }

    const byMode = (loraMode, upscaleMode) => result.scenarios.find(
      (item) => item.loraMode === loraMode && item.upscaleMode === upscaleMode,
    );
    const base = byMode(0, 0);
    const loraBypass = byMode(4, 0);
    const loraMute = byMode(2, 0);
    const upscaleBypass = byMode(0, 4);
    const upscaleMute = byMode(0, 2);

    const checks = [];
    const check = (name, condition, detail) => {
      checks.push({ name, ok: !!condition, detail });
    };

    check('基线：全部节点都在 api prompt 里', base.promptNodeCount === 7, base.promptNodeIds.join(','));
    check('基线：sampler.model 指向 LoraLoader', base.samplerModel && base.samplerModel[0] === base.ids.lora, JSON.stringify(base.samplerModel));
    check('基线：sampler.latent_image 指向 LatentUpscale', base.samplerLatent && base.samplerLatent[0] === base.ids.upscale, JSON.stringify(base.samplerLatent));

    check('绕过 LoraLoader：LoraLoader 不在 api prompt 里', !loraBypass.loraInPrompt, loraBypass.promptNodeIds.join(','));
    check('绕过 LoraLoader：sampler.model 改接上游 CheckpointLoaderSimple',
      loraBypass.samplerModel && loraBypass.samplerModel[0] === loraBypass.ids.loader, JSON.stringify(loraBypass.samplerModel));
    check('绕过 LoraLoader：其余节点数量减少 1', loraBypass.promptNodeCount === base.promptNodeCount - 1, String(loraBypass.promptNodeCount));

    check('静音 LoraLoader：LoraLoader 不在 api prompt 里', !loraMute.loraInPrompt, loraMute.promptNodeIds.join(','));
    check('静音 LoraLoader：其余节点数量减少 1', loraMute.promptNodeCount === base.promptNodeCount - 1, String(loraMute.promptNodeCount));

    check('绕过 LatentUpscale：LatentUpscale 不在 api prompt 里', !upscaleBypass.upscaleInPrompt, upscaleBypass.promptNodeIds.join(','));
    check('绕过 LatentUpscale：sampler.latent_image 改接上游 EmptyLatentImage',
      upscaleBypass.samplerLatent && upscaleBypass.samplerLatent[0] === upscaleBypass.ids.empty, JSON.stringify(upscaleBypass.samplerLatent));

    check('静音 LatentUpscale：LatentUpscale 不在 api prompt 里', !upscaleMute.upscaleInPrompt, upscaleMute.promptNodeIds.join(','));
    check('静音 LatentUpscale：其余节点数量减少 1', upscaleMute.promptNodeCount === base.promptNodeCount - 1, String(upscaleMute.promptNodeCount));

    check('工作流序列化带出 nodes[].mode（App 侧工作副本同步依据）',
      loraBypass.serializedModes[loraBypass.ids.lora] === 4 && loraMute.serializedModes[loraMute.ids.lora] === 2,
      `bypass=${JSON.stringify(loraBypass.serializedModes)} mute=${JSON.stringify(loraMute.serializedModes)}`);

    console.log('真实前端核验：ComfyUI ' + SERVER_URL);
    result.scenarios.forEach((item) => {
      console.log(
        `  节点 id=${JSON.stringify(item.ids)} lora.mode=${item.loraMode} upscale.mode=${item.upscaleMode} -> ` +
        `prompt 节点 ${item.promptNodeCount} 个 [${item.promptNodeIds.join(',')}] ` +
        `sampler.model=${JSON.stringify(item.samplerModel)} sampler.latent_image=${JSON.stringify(item.samplerLatent)} ` +
        `序列化 mode=${JSON.stringify(item.serializedModes)}`,
      );
    });
    checks.forEach((item) => {
      console.log(`${item.ok ? 'PASS' : 'FAIL'}: ${item.name}（${item.detail}）`);
    });
    const failed = checks.filter((item) => !item.ok);
    if (failed.length) {
      console.error('FAIL: ' + failed.length + '/' + checks.length + ' 项未通过');
      process.exitCode = 1;
    } else {
      console.log('PASS: 真实前端确认 mode 由构图阶段解析（0 始终执行 / 2 静音 / 4 绕过）');
    }
    console.log('--- 绕过 LoraLoader 的 api prompt ---');
    console.log(JSON.stringify(loraBypass.prompt, null, 2));
  } finally {
    try { if (client) client.socket.close(); } catch (_) { /* ignore */ }
    browser.kill();
    await sleep(500);
    try {
      fs.rmSync(profileDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 300 });
    } catch (error) {
      console.error('（临时浏览器目录未清理干净：' + error.message + '）');
    }
  }
}

main().catch((error) => {
  console.error('FAIL:', error && error.message ? error.message : error);
  process.exitCode = 1;
});
