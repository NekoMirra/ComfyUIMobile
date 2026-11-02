package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.ServerAsset

/**
 * 服务器资产分页的纯逻辑。
 *
 * 偏移量由客户端推进：请求第 N 页时 offset = 已请求过的条数，而不是已加载的条数，
 * 否则一旦某一页出现重复条目，翻页就会漏掉服务器上的记录。
 */
object ServerAssetPagination {
    /** 下一页的请求偏移。 */
    fun nextOffset(currentOffset: Int, pageSize: Int): Int = currentOffset + pageSize

    /** 合并两页资产，按 id 去重，已有的条目保持原顺序。 */
    fun <T> merge(existing: List<T>, page: List<T>, idOf: (T) -> String): List<T> {
        if (existing.isEmpty()) return page
        val seen = existing.mapTo(HashSet(), idOf)
        return existing + page.filter { seen.add(idOf(it)) }
    }

    /** 合并两页资产，按 id 去重，已有的条目保持原顺序。 */
    fun merge(existing: List<ServerAsset>, page: List<ServerAsset>): List<ServerAsset> =
        merge(existing, page) { it.id }

    /** 还有下一页的条件：服务器说有下一页，并且已加载的条数还没有达到总数。 */
    fun hasMore(loaded: Int, total: Int, serverHasMore: Boolean): Boolean =
        serverHasMore && loaded < total
}
