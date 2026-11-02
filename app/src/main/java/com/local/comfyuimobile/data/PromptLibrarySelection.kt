package com.local.comfyuimobile.data

/**
 * 提示词库在界面上的选择逻辑。
 *
 * 与 [PromptLibraryLogic] 分开：前者管数据（增删改查、去重、置顶），这里管"用户选中了
 * 什么、界面怎么显示"，两者的边界让界面逻辑可以直接单测。
 */
object PromptLibrarySelection {
    /**
     * 浏览模式下的可见条目：先按关键字与分类筛选，再按"收藏优先、最近使用"排序。
     */
    fun visible(entries: List<PromptEntry>, keyword: String, category: String?): List<PromptEntry> =
        PromptLibraryLogic.ordered(PromptLibraryLogic.search(entries, keyword, category))

    /**
     * 确认选择后写进 MULTILINE 字段的文本。
     *
     * 同一段文字只写一次（去重），多条之间换行分隔。
     */
    fun join(entries: List<PromptEntry>): String =
        entries.map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n")
}
