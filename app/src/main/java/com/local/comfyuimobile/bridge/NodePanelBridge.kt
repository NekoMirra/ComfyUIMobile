package com.local.comfyuimobile.bridge

import org.json.JSONObject

/**
 * 节点自带面板（插件面板）的注入脚本。
 *
 * 面板本身（弹窗、预览、iframe）由插件在官方前端页面里创建，App 不复刻插件实现，
 * 只负责：把该节点在官方前端里定位出来、写入当前编辑值、点开它的按钮、把改动读回来。
 * 因此任何插件面板都走同一条路径，不需要为每个插件单独适配。
 *
 * 所有脚本都返回 JSON 字符串：成功为 {"ok":true,...}，失败为 {"ok":false,"error":"..."}，
 * 失败必须显式返回，不允许静默成功。
 */
data class NodePanelOpenResult(
    val ok: Boolean,
    val error: String? = null,
    val changed: Map<String, String> = emptyMap(),
)

object NodePanelBridge {
    /** 节点 id 只能是普通标识符字符，避免注入到脚本里的越界内容。 */
    private val NODE_ID_PATTERN = Regex("[A-Za-z0-9_.\\-]{1,64}")

    private const val READY_PROBE_JS = """
        String(
          !!(window.comfyAPI && window.comfyAPI.app && window.comfyAPI.app.app && window.comfyAPI.app.app.graph) &&
          !!(window.LiteGraph && window.LiteGraph.registered_node_types &&
            Object.keys(window.LiteGraph.registered_node_types).length > 0)
          ? '1' : '0'
        )
    """

    private const val FIND_NODE_JS = """
        const __comfyFindNode = (graph, id) => {
          const tryGet = (value) => {
            try {
              if (typeof graph.getNodeById === 'function') return graph.getNodeById(value) || null;
            } catch (_) { /* 部分前端版本只接受数字 id */ }
            return null;
          };
          return tryGet(Number(id)) || tryGet(id) ||
            (graph._nodes || []).find(node => node && String(node.id) === String(id)) || null;
        };
    """

    private const val COLLECT_VALUES_JS = """
        const __comfyCollectWidgetValues = (node) => {
          const out = {};
          for (const widget of (node.widgets || [])) {
            if (!widget || !widget.name) continue;
            if (widget.type === 'button') continue;
            if (widget.element) continue;
            if (widget.value === undefined) continue;
            try { out[widget.name] = JSON.parse(JSON.stringify(widget.value)); }
            catch (_) { out[widget.name] = String(widget.value); }
          }
          return out;
        };
    """

    private const val FOCUS_NODE_JS = """
        const __comfyFocusNode = (app, node) => {
          const canvas = app && app.canvas;
          if (!canvas) return false;
          try {
            const pos = Array.isArray(node.pos)
              ? node.pos
              : [Number(node.pos && node.pos.x) || 0, Number(node.pos && node.pos.y) || 0];
            const size = Array.isArray(node.size)
              ? node.size
              : [Number(node.size && node.size.x) || 0, Number(node.size && node.size.y) || 0];
            let centered = false;
            if (typeof canvas.centerOnNode === 'function') {
              try { canvas.centerOnNode(node); centered = true; } catch (_) {}
            }
            if (!centered && canvas.ds) {
              try {
                const scale = Number(canvas.ds.scale);
                if (Number.isFinite(scale) && scale > 0) {
                  canvas.ds.scale = Math.max(0.6, Math.min(1.5, scale));
                }
                canvas.ds.offset[0] = -pos[0] - size[0] / 2;
                canvas.ds.offset[1] = -pos[1] - size[1] / 2;
              } catch (_) {}
            }
            if (typeof canvas.setDirty === 'function') canvas.setDirty(true, true);
            if (typeof canvas.draw === 'function') canvas.draw(true, true);
            return true;
          } catch (_) {
            return false;
          }
        };
    """

    private const val APP_LOOKUP_JS = """
        const app = window.__comfyMobileApp ||
          (window.comfyAPI && window.comfyAPI.app && window.comfyAPI.app.app);
        if (!app) return JSON.stringify({ok:false, error:'ComfyUI 前端对象尚未就绪'});
        const graph = app.rootGraph || app.graph;
        if (!graph) return JSON.stringify({ok:false, error:'ComfyUI 画布尚未就绪'});
    """

    /**
     * 官方前端是否就绪的探针表达式，返回 "1" 表示就绪。
     *
     * 两个条件缺一不可：`comfyAPI.app.app.graph` 存在，且节点注册表非空
     * （扩展节点还没注册时建图只会得到占位节点）。
     */
    fun readyProbe(): String = READY_PROBE_JS.trimIndent()

    /**
     * 打开节点面板的脚本。
     *
     * 依次完成：定位节点 → 把 [initialValues] 写进对应 widget（跳过 button 与 DOM widget）
     * → 把画布平移到该节点 → 必要时点开名为 [widgetName] 的按钮。
     * 打开时会把该节点的可提交 widget 值记为基线，供 [buildReadValuesScript] 比较出
     * 「面板真正改动过的参数」。
     */
    fun buildOpenPanelScript(
        nodeId: String,
        widgetName: String? = null,
        initialValues: Map<String, String> = emptyMap(),
    ): String {
        val idLiteral = JSONObject.quote(requireNodeId(nodeId))
        val widgetLiteral = widgetName?.let { JSONObject.quote(it) } ?: "null"
        val values = JSONObject()
        initialValues.forEach { (key, value) -> values.put(key, value) }
        val valuesLiteral = values.toString()
        return """
        (async () => {
          try {
            $APP_LOOKUP_JS
            $FIND_NODE_JS
            $COLLECT_VALUES_JS
            $FOCUS_NODE_JS
            const node = __comfyFindNode(graph, $idLiteral);
            if (!node) return JSON.stringify({ok:false, error:'找不到节点：' + $idLiteral});
            const canvas = app.canvas;
            const written = [];
            const skipped = [];
            const initial = $valuesLiteral;
            for (const name of Object.keys(initial)) {
              const widget = (node.widgets || []).find(item => item && item.name === name);
              if (!widget) { skipped.push(name); continue; }
              if (widget.type === 'button') { skipped.push(name); continue; }
              if (widget.element) { skipped.push(name); continue; }
              const raw = initial[name];
              let value = raw;
              if (typeof widget.value === 'number' && /^-?\d+(\.\d+)?$/.test(String(raw))) {
                value = Number(raw);
              } else if (typeof widget.value === 'boolean' && /^(true|false)$/i.test(String(raw))) {
                value = String(raw).toLowerCase() === 'true';
              }
              let applied = false;
              if (typeof widget.setValue === 'function') {
                try { widget.setValue(value, {node: node, canvas: canvas}); applied = true; } catch (_) {}
              }
              if (!applied) {
                widget.value = value;
                const input = widget.inputEl || widget.element;
                if (input && 'value' in input) input.value = value;
                if (typeof widget.callback === 'function') {
                  try { widget.callback(value, canvas, node); } catch (_) {}
                }
              } else {
                const input = widget.inputEl || widget.element;
                if (input && 'value' in input) input.value = value;
              }
              written.push(name);
            }
            window.__comfyMobileNodePanelBaselines = window.__comfyMobileNodePanelBaselines || {};
            window.__comfyMobileNodePanelBaselines[$idLiteral] = __comfyCollectWidgetValues(node);
            const focused = __comfyFocusNode(app, node);
            let panelDetected = false;
            const panelWidgetName = $widgetLiteral;
            if (panelWidgetName) {
              const panelWidget = (node.widgets || []).find(item => item && item.name === panelWidgetName);
              if (!panelWidget) {
                return JSON.stringify({ok:false, error:'节点上没有该面板：' + panelWidgetName});
              }
              let triggered = false;
              if (typeof panelWidget.callback === 'function') {
                panelWidget.callback(null, canvas, node);
                triggered = true;
              } else if (typeof panelWidget.click === 'function') {
                panelWidget.click();
                triggered = true;
              }
              if (!triggered) {
                return JSON.stringify({ok:false, error:'该控件没有可调用的回调：' + panelWidgetName});
              }
              await new Promise(resolve => setTimeout(resolve, 600));
              panelDetected = !!document.querySelector(
                'dialog, [role="dialog"], [aria-modal="true"], .modal, .comfy-modal, '
                + '[class*="overlay"], [class*="dialog"]'
              );
            }
            const baseline = window.__comfyMobileNodePanelBaselines[$idLiteral] || {};
            const current = __comfyCollectWidgetValues(node);
            const changed = {};
            for (const name of Object.keys(current)) {
              if (!(name in baseline) || JSON.stringify(baseline[name]) !== JSON.stringify(current[name])) {
                changed[name] = current[name];
              }
            }
            return JSON.stringify({
              ok:true,
              type:String(node.comfyClass || node.type || ''),
              panelDetected:panelDetected,
              focused:focused,
              written:written,
              skipped:skipped,
              changed:changed
            });
          } catch (error) {
            return JSON.stringify({ok:false, error:'打开节点面板失败：' + String((error && error.stack) || error)});
          }
        })()
        """.trimIndent()
    }

    /**
     * 读回该节点所有可提交 widget 的当前值（跳过 button 与 DOM widget），
     * 并与打开面板时记录的基线比较，给出「面板真正改动过的参数」。
     */
    fun buildReadValuesScript(nodeId: String): String {
        val idLiteral = JSONObject.quote(requireNodeId(nodeId))
        return """
        (async () => {
          try {
            $APP_LOOKUP_JS
            $FIND_NODE_JS
            $COLLECT_VALUES_JS
            const node = __comfyFindNode(graph, $idLiteral);
            if (!node) return JSON.stringify({ok:false, error:'找不到节点：' + $idLiteral});
            const current = __comfyCollectWidgetValues(node);
            const baselines = window.__comfyMobileNodePanelBaselines || {};
            const baseline = baselines[$idLiteral] || {};
            const changed = {};
            for (const name of Object.keys(current)) {
              if (!(name in baseline) || JSON.stringify(baseline[name]) !== JSON.stringify(current[name])) {
                changed[name] = current[name];
              }
            }
            return JSON.stringify({ok:true, values:current, changed:changed});
          } catch (error) {
            return JSON.stringify({ok:false, error:'读取节点面板参数失败：' + String((error && error.stack) || error)});
          }
        })()
        """.trimIndent()
    }

    /** 只把官方前端的画布平移到该节点，用于节点图点击后的定位。 */
    fun buildFocusNodeScript(nodeId: String): String {
        val idLiteral = JSONObject.quote(requireNodeId(nodeId))
        return """
        (async () => {
          try {
            $APP_LOOKUP_JS
            $FIND_NODE_JS
            $FOCUS_NODE_JS
            const node = __comfyFindNode(graph, $idLiteral);
            if (!node) return JSON.stringify({ok:false, error:'找不到节点：' + $idLiteral});
            const focused = __comfyFocusNode(app, node);
            return JSON.stringify({ok:true, focused:focused, type:String(node.comfyClass || node.type || '')});
          } catch (error) {
            return JSON.stringify({ok:false, error:'定位节点失败：' + String((error && error.stack) || error)});
          }
        })()
        """.trimIndent()
    }

    private fun requireNodeId(nodeId: String): String {
        require(NODE_ID_PATTERN.matches(nodeId)) { "非法节点 id：$nodeId" }
        return nodeId
    }
}
