package com.local.comfyuimobile.network

/**
 * 节点图上「正在执行」的可见状态。
 *
 * 同一时刻只保留当前正在执行的节点：上一个节点结束后它必须熄灯，
 * 否则工作流跑完前，图上会留下一串永远亮着、进度条冻在最后一帧的旧节点，
 * 与参数页「当前执行部件」的高亮也对不上。
 *
 * 纯数据，无 Android 依赖，便于单测。
 */
data class VisibleExecution(
    val executingNodeIds: Set<String>,
    val nodeProgress: Map<String, Float>,
) {
    /**
     * 节点 [nodeId] 开始执行。
     *
     * @param progress 该节点的进度（0f..1f）；为 null 表示只知道它在跑，还没有进度值。
     * @return 新的可见状态。空白节点 id 表示没有有效节点，保持原状态不变。
     */
    fun executing(nodeId: String, progress: Float?): VisibleExecution {
        val id = nodeId.trim()
        if (id.isEmpty()) return this
        return VisibleExecution(
            executingNodeIds = setOf(id),
            nodeProgress = if (progress != null) mapOf(id to progress.coerceIn(0f, 1f)) else emptyMap(),
        )
    }

    companion object {
        /** 尚未开始执行：图上没有任何高亮。 */
        fun idle(): VisibleExecution = VisibleExecution(emptySet(), emptyMap())
    }
}
