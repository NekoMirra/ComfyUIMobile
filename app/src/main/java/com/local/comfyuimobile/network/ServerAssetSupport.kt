package com.local.comfyuimobile.network

/**
 * 「服务器图片 / 历史生成」的服务端能力门槛。
 *
 * 这两个功能依赖 ComfyUI 的 `/api/assets` 与 `/api/jobs` 接口，服务端是否提供
 * 由 `GET /features` 返回的 `assets` 能力位决定。客户端在连接时随其它服务器
 * 能力一起读到这个位并缓存，因此这里不需要额外请求。
 *
 * 判定结果有两种：
 * - 能力位为 true：可以访问。
 * - 能力位为 false：服务端没有启用资源功能，或者版本太旧（没有这两个接口），
 *   属于同一类降级。此时直接给出明确提示，不要发出注定失败的请求。
 *
 * 能力未知（还没有探测过，或探测失败）时返回 null 放行：此时不能断言服务端
 * 没有该功能，仍由请求失败后的既有兜底路径给出提示。
 */
object ServerAssetSupport {
    /** 能力位为 false 时统一使用的降级文案。 */
    const val DISABLED_MESSAGE = "服务器未启用资源功能，无法读取服务器图片"

    /**
     * @param capabilities 已缓存的服务器能力；null 表示能力未知。
     * @return 需要提示降级的文案；null 表示可以正常访问。
     */
    fun assetsDisabledMessage(capabilities: ServerCapabilities?): String? {
        if (capabilities == null) return null
        if (capabilities.assetsEnabled) return null
        return DISABLED_MESSAGE
    }
}
