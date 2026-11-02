package com.local.comfyuimobile.bridge

import com.local.comfyuimobile.model.NodeMode
import org.json.JSONException
import org.json.JSONObject

/**
 * 节点级绕过 / 静音策略。
 *
 * ComfyUI API prompt 中节点对象的 "mode" 字段语义：0 = 始终执行，2 = 静音（节点不执行，
 * 下游仍按连线取值），4 = 绕过（下游直接接到该节点的上游输出）。
 *
 * 官方前端的 graphToPrompt() 只产出 mode = 0 的 prompt，所以 App 侧的用户选择必须在
 * 提交前由本策略写回 prompt JSON。
 */
object NodeModePolicy {
    /** 把任意整数归一到合法的节点模式，非法值一律回落到 [NodeMode.ALWAYS]。 */
    fun sanitize(value: Int): NodeMode = when (value) {
        NodeMode.ALWAYS.wireValue -> NodeMode.ALWAYS
        NodeMode.MUTE.wireValue -> NodeMode.MUTE
        NodeMode.BYPASS.wireValue -> NodeMode.BYPASS
        else -> NodeMode.ALWAYS
    }

    /**
     * 把 [modes] 写进 API prompt JSON。
     *
     * prompt 形如 {"1": {"class_type": "...", "inputs": {...}}}，节点 id 就是对象键。
     * 只有出现在 prompt 里的节点会被写入，其余节点和字段保持不变。
     *
     * @throws IllegalArgumentException prompt 不是合法 JSON 对象时抛出。
     */
    fun applyToPrompt(promptJson: String, modes: Map<String, NodeMode>): String {
        if (modes.isEmpty()) return promptJson
        val root = parsePrompt(promptJson)
        modes.forEach { (nodeId, mode) ->
            val node = root.optJSONObject(nodeId) ?: return@forEach
            node.put("mode", sanitize(mode.wireValue).wireValue)
        }
        return root.toString()
    }

    /** 读出 prompt 中所有 mode != 0（静音或绕过）的节点 id，供界面标记。 */
    fun collectBypassedNodes(promptJson: String): Set<String> {
        val root = parsePrompt(promptJson)
        return buildSet {
            root.keys().forEach { nodeId ->
                val node = root.optJSONObject(nodeId) ?: return@forEach
                if (node.optInt("mode", NodeMode.ALWAYS.wireValue) != NodeMode.ALWAYS.wireValue) {
                    add(nodeId)
                }
            }
        }
    }

    /** 合并一次模式变更，返回新映射，不修改入参。 */
    fun mergeModes(current: Map<String, NodeMode>, nodeId: String, mode: NodeMode): Map<String, NodeMode> =
        current + (nodeId to sanitize(mode.wireValue))

    private fun parsePrompt(promptJson: String): JSONObject = try {
        JSONObject(promptJson)
    } catch (error: JSONException) {
        throw IllegalArgumentException("节点模式无法解析 API prompt JSON：${error.message}", error)
    }
}
