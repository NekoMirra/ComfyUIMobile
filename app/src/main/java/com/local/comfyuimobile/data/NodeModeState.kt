package com.local.comfyuimobile.data

import com.local.comfyuimobile.bridge.NodeModePolicy
import com.local.comfyuimobile.model.NodeMode
import com.local.comfyuimobile.model.WorkflowNode

/** 一次节点模式切换的结果。 */
sealed interface NodeModeChange {
    /** 切换成功，[modes] 是切换后的完整模式表。 */
    data class Applied(val modes: Map<String, NodeMode>) : NodeModeChange

    /** 切换被拒绝，[reason] 说明原因（节点不存在时不会静默忽略）。 */
    data class Rejected(val reason: String) : NodeModeChange
}

/**
 * 节点模式（始终执行 / 静音 / 绕过）的状态机。
 *
 * mode 的语义由官方前端构图时解析，服务端不消费 api prompt 里的 mode 字段，
 * 所以模式必须落到工作流 JSON 的 nodes[].mode（见 [NodeModePolicy]）。
 */
object NodeModeState {
    /**
     * 切换一个节点的模式。
     *
     * @param knownNodeIds 当前工作流里存在的节点 id；节点不在其中时直接拒绝，
     *        并给出可读的原因，不会静默忽略。
     */
    fun apply(
        current: Map<String, NodeMode>,
        nodeId: String,
        mode: NodeMode,
        knownNodeIds: Set<String>,
    ): NodeModeChange {
        if (nodeId.isBlank()) return NodeModeChange.Rejected("节点 id 为空，无法设置节点模式")
        if (nodeId !in knownNodeIds) {
            return NodeModeChange.Rejected("当前工作流里找不到节点 $nodeId，无法设置节点模式")
        }
        val sanitized = NodeModePolicy.sanitize(mode.wireValue)
        return NodeModeChange.Applied(NodeModePolicy.mergeModes(current, nodeId, sanitized))
    }

    /**
     * 生成时真正交给前端的模式表。
     *
     * 只保留工作流里仍然存在的节点：工作流被重新载入后，旧节点 id 会失效，
     * 把它们继续写进画布只会让构图失败。
     */
    fun modesForBuild(modes: Map<String, NodeMode>, nodes: List<WorkflowNode>): Map<String, NodeMode> {
        if (modes.isEmpty()) return emptyMap()
        val known = nodes.mapTo(HashSet()) { it.id }
        return modes.filterKeys { it in known }
    }

    /** 被 [modesForBuild] 丢掉的节点 id，用于在日志里明确说明，而不是静默丢弃。 */
    fun droppedNodeIds(modes: Map<String, NodeMode>, nodes: List<WorkflowNode>): List<String> {
        if (modes.isEmpty()) return emptyList()
        val known = nodes.mapTo(HashSet()) { it.id }
        return modes.keys.filter { it !in known }
    }
}
