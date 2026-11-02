package com.local.comfyuimobile.bridge

import com.local.comfyuimobile.model.NodeMode
import org.json.JSONException
import org.json.JSONObject

/**
 * 节点级绕过 / 静音策略。
 *
 * mode 是 LiteGraph 的节点模式：0 = 始终执行，2 = 静音（NEVER，节点不执行），
 * 4 = 绕过（BYPASS，下游直接接该节点的上游输出）。
 *
 * 语义由官方前端在构图（graphToPrompt）时解析：mode 为 2 / 4 的节点不会出现在
 * api prompt 里。服务端只消费 prompt 的 class_type 与 inputs，并不读取 prompt 里
 * 的 mode 字段，所以 App 侧唯一正确的落点是工作流 JSON 的 nodes[].mode（或画布上
 * 节点的 node.mode），本文件只负责工作流 JSON 这一层。
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
     * 把 [modes] 写进工作流 JSON 的 nodes[].mode。
     *
     * 节点按 id 匹配：工作流里的 id 可能是数字，界面上的 id 是字符串，两种写法
     * 都能匹配到同一个节点。除 mode 外，节点与工作流的其余字段保持不变。
     *
     * @throws IllegalArgumentException 工作流 JSON 非法、没有 nodes 数组，或某个
     *         节点 id 在工作流里不存在时抛出（不存在时不会静默忽略）。
     */
    fun applyToWorkflowJson(workflowJson: String, modes: Map<String, NodeMode>): String {
        if (modes.isEmpty()) return workflowJson
        val root = parseWorkflow(workflowJson)
        val nodes = root.optJSONArray("nodes")
            ?: throw IllegalArgumentException("工作流 JSON 里没有 nodes 数组，无法写入节点模式")
        val byId = HashMap<String, JSONObject>()
        for (index in 0 until nodes.length()) {
            val node = nodes.optJSONObject(index) ?: continue
            val id = node.opt("id") ?: continue
            byId[nodeKey(id)] = node
        }
        val missing = mutableListOf<String>()
        modes.forEach { (nodeId, mode) ->
            val node = byId[nodeKey(nodeId)]
            if (node == null) {
                missing += nodeId
                return@forEach
            }
            node.put("mode", sanitize(mode.wireValue).wireValue)
        }
        if (missing.isNotEmpty()) {
            throw IllegalArgumentException("工作流里找不到节点：${missing.joinToString(", ")}")
        }
        return root.toString()
    }

    /** 读回各节点的模式，缺省视为 [NodeMode.ALWAYS]。 */
    fun collectModesFromWorkflowJson(workflowJson: String): Map<String, NodeMode> {
        val root = parseWorkflow(workflowJson)
        val nodes = root.optJSONArray("nodes") ?: return emptyMap()
        val modes = LinkedHashMap<String, NodeMode>()
        for (index in 0 until nodes.length()) {
            val node = nodes.optJSONObject(index) ?: continue
            val id = node.opt("id") ?: continue
            modes[nodeKey(id)] = sanitize(node.optInt("mode", NodeMode.ALWAYS.wireValue))
        }
        return modes
    }

    /** 合并一次模式变更，返回新映射，不修改入参。 */
    fun mergeModes(current: Map<String, NodeMode>, nodeId: String, mode: NodeMode): Map<String, NodeMode> =
        current + (nodeId to sanitize(mode.wireValue))

    /** 节点 id 在工作流里可能是数字，界面里是字符串，统一成字符串比较。 */
    private fun nodeKey(id: Any): String = id.toString()

    private fun parseWorkflow(workflowJson: String): JSONObject = try {
        JSONObject(workflowJson)
    } catch (error: JSONException) {
        throw IllegalArgumentException("节点模式无法解析工作流 JSON：${error.message}", error)
    }
}
