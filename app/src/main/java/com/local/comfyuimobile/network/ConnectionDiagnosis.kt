package com.local.comfyuimobile.network

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * The concrete reason a connection attempt did not work. The connection layer
 * maps low level exceptions to these cases, the diagnosis layer turns them into
 * a Chinese explanation with actionable advice.
 */
sealed class ConnectionFailure {
    /** The address could not be parsed at all. */
    data class InvalidAddress(val address: String, val reason: String = "") : ConnectionFailure()

    /** The TCP port refused the connection. */
    data class ConnectionRefused(val address: String, val scheme: String) : ConnectionFailure()

    /** The TLS certificate could not be verified. */
    data class TlsCertificateError(val address: String, val detail: String = "") : ConnectionFailure()

    /** The address answered with an HTML page, usually a platform login page. */
    data class HtmlLoginPage(val address: String) : ConnectionFailure()

    /** The server requires a newer frontend than the one in use. */
    data class FrontendTooOld(val address: String, val required: String, val installed: String) : ConnectionFailure()

    /** The server did not report any frontend version at all. */
    data class FrontendMissing(val address: String) : ConnectionFailure()

    /** The root path answered with an error status, so it is not ComfyUI. */
    data class NotComfyUi(val address: String, val statusCode: Int = 404) : ConnectionFailure()

    /** The server did not answer in time. */
    data class Timeout(val address: String, val waitedMillis: Long = 0L) : ConnectionFailure()

    /** Anything that does not fit the known cases. */
    data class Unknown(val address: String, val detail: String = "") : ConnectionFailure()
}

enum class DiagnosisKind {
    INVALID_ADDRESS,
    CONNECTION_REFUSED,
    TLS_CERTIFICATE,
    HTML_LOGIN_PAGE,
    FRONTEND_TOO_OLD,
    FRONTEND_MISSING,
    NOT_COMFYUI,
    TIMEOUT,
    UNKNOWN,
}

/** Chinese explanation of a failed connection plus the steps that usually fix it. */
data class Diagnosis(
    val diagnosis: DiagnosisKind,
    val title: String,
    val detail: String,
    val actions: List<String>,
)

/**
 * Turns a [ConnectionFailure] into a Chinese diagnosis. The function is pure:
 * same input, same words, no dependency on Android or the network.
 */
fun diagnose(baseUrl: String, failure: ConnectionFailure): Diagnosis = when (failure) {
    is ConnectionFailure.InvalidAddress -> Diagnosis(
        diagnosis = DiagnosisKind.INVALID_ADDRESS,
        title = "服务器地址格式不正确",
        detail = "无法解析地址「${failure.address}」${failure.reason.takeIf { it.isNotBlank() }?.let { "：$it" } ?: ""}。" +
            "本 App 只接受 http:// 或 https:// 开头的 ComfyUI 根地址。",
        actions = listOf(
            "改用 http:// 开头，例如 http://192.168.1.20:8188",
            "只填写实例根地址，不要带 /prompt、/api 等子路径",
            "确认端口号：HTTP 默认 8188，HTTPS 使用平台分配的端口",
        ),
    )

    is ConnectionFailure.ConnectionRefused -> {
        val secure = failure.scheme.equals("https", ignoreCase = true)
        Diagnosis(
            diagnosis = DiagnosisKind.CONNECTION_REFUSED,
            title = if (secure) "HTTPS 端口拒绝连接" else "无法连接到服务器端口",
            detail = if (secure) {
                "$failure.address 的 HTTPS 端口拒绝连接。该端口没有 HTTPS 服务在监听，通常是写成了 HTTP 服务端口，或云端实例尚未启动。"
            } else {
                "$failure.address 的端口拒绝连接。该端口没有服务在监听，或被防火墙拦截。"
            },
            actions = listOf(
                "检查 ComfyUI 是否以 --listen 0.0.0.0 启动",
                if (secure) {
                    "确认地址使用 https:// 与平台分配的端口；若是本地实例通常应使用 http://192.168.x.x:8188"
                } else {
                    "确认地址使用 http:// 并指向 8188 端口"
                },
                "确认手机与服务器在同一局域网，且访客网络没有开启设备隔离",
            ),
        )
    }

    is ConnectionFailure.TlsCertificateError -> Diagnosis(
        diagnosis = DiagnosisKind.TLS_CERTIFICATE,
        title = "HTTPS 证书校验失败",
        detail = "$failure.address 的 TLS 证书校验未通过${failure.detail.takeIf { it.isNotBlank() }?.let { "：$it" } ?: ""}。" +
            "常见原因是自签名证书、证书过期或证书域名与地址不一致。",
        actions = listOf(
            "改用该实例的 http:// 直连地址（若服务允许）",
            "为服务器配置受信任的证书，证书域名需与地址一致",
            "不要在地址中携带账号、令牌或平台登录链接",
        ),
    )

    is ConnectionFailure.HtmlLoginPage -> Diagnosis(
        diagnosis = DiagnosisKind.HTML_LOGIN_PAGE,
        title = "该地址返回的是平台登录页",
        detail = "$failure.address 返回的是 HTML 网页而不是 ComfyUI 接口，通常是平台控制台、分享页或需要登录的页面。",
        actions = listOf(
            "该地址返回的是平台登录页，本 App 需要自带前端的完整实例",
            "在平台控制台复制实例的 API 根地址，不要使用控制台或分享页链接",
            "确认该实例已部署官方前端，并且免登录访问 /system_stats 能返回 JSON",
        ),
    )

    is ConnectionFailure.FrontendTooOld -> Diagnosis(
        diagnosis = DiagnosisKind.FRONTEND_TOO_OLD,
        title = "服务器前端版本过低",
        detail = "$failure.address 要求前端 ${failure.required.ifBlank { "未知" }}，当前只有 ${failure.installed.ifBlank { "未知" }}。" +
            "前端过旧会让工作流编辑、预览等界面功能无法使用。",
        actions = listOf(
            "在服务器升级前端包：pip install -U comfyui-frontend-package",
            "确认实例使用完整官方前端，而不是只暴露 API 的精简镜像",
            "升级后重新连接并刷新服务器能力",
        ),
    )

    is ConnectionFailure.FrontendMissing -> Diagnosis(
        diagnosis = DiagnosisKind.FRONTEND_MISSING,
        title = "服务器缺少前端",
        detail = "$failure.address 没有报告前端版本，本 App 无法加载 ComfyUI 界面，只能使用部分接口。",
        actions = listOf(
            "在服务器安装官方前端包：pip install -U comfyui-frontend-package",
            "确认实例是完整 ComfyUI，而不是仅提供 API 的托管镜像",
            "安装完成后重启 ComfyUI 再重新连接",
        ),
    )

    is ConnectionFailure.NotComfyUi -> Diagnosis(
        diagnosis = DiagnosisKind.NOT_COMFYUI,
        title = "该地址不是 ComfyUI 服务",
        detail = "$failure.address 返回 HTTP ${failure.statusCode}，根地址不是 ComfyUI 接口。",
        actions = listOf(
            "确认地址指向 ComfyUI 根地址，而不是反向代理子路径或管理后台",
            "在浏览器打开 ${failure.address}/system_stats，能返回 JSON 才说明接口可用",
            "带路径前缀的地址本 App 暂不支持，请改用实例根地址",
        ),
    )

    is ConnectionFailure.Timeout -> Diagnosis(
        diagnosis = DiagnosisKind.TIMEOUT,
        title = "连接服务器超时",
        detail = "等待 $failure.address 响应超时${if (failure.waitedMillis > 0L) "（等待 ${failure.waitedMillis / 1000L} 秒）" else ""}。" +
            "服务器可能未启动、网络不通，或被代理拦截。",
        actions = listOf(
            "确认 ComfyUI 正在运行，并且以 --listen 0.0.0.0 启动",
            "在同一网络下用浏览器打开 $failure.address 验证可达性",
            "关闭手机代理或 VPN 后重试，也可以改用局域网直连地址",
        ),
    )

    is ConnectionFailure.Unknown -> Diagnosis(
        diagnosis = DiagnosisKind.UNKNOWN,
        title = "无法连接到服务器",
        detail = "连接 $failure.address 失败${failure.detail.takeIf { it.isNotBlank() }?.let { "：$it" } ?: ""}。",
        actions = listOf(
            "确认地址格式为 http:// 或 https:// 开头的实例根地址",
            "导出诊断日志，在反馈问题时附上完整错误",
            "重启服务器后重新连接",
        ),
    )
}

/**
 * Maps a raw exception to a [ConnectionFailure]. The address is used for the
 * message only, the classification is based on the exception type.
 */
fun classifyFailure(baseUrl: String, error: Throwable): ConnectionFailure = when (error) {
    is IllegalArgumentException -> ConnectionFailure.InvalidAddress(baseUrl, error.message.orEmpty())
    is UnsupportedEndpointException -> ConnectionFailure.NotComfyUi(baseUrl, error.statusCode)
    is MalformedResponseException -> ConnectionFailure.HtmlLoginPage(baseUrl)
    is ServerTlsException -> ConnectionFailure.TlsCertificateError(baseUrl, error.message.orEmpty())
    is ServerTimeoutException -> ConnectionFailure.Timeout(baseUrl)
    is ServerUnreachableException -> classifyIoFailure(baseUrl, error.cause ?: error)
    is SSLException -> ConnectionFailure.TlsCertificateError(baseUrl, error.message.orEmpty())
    is SocketTimeoutException -> ConnectionFailure.Timeout(baseUrl)
    is ConnectException -> ConnectionFailure.ConnectionRefused(baseUrl, schemeOf(baseUrl))
    is UnknownHostException -> ConnectionFailure.Unknown(baseUrl, "域名无法解析")
    is NoRouteToHostException -> ConnectionFailure.Unknown(baseUrl, "没有到该主机的路由")
    is PortUnreachableException -> ConnectionFailure.ConnectionRefused(baseUrl, schemeOf(baseUrl))
    is IOException -> ConnectionFailure.Unknown(baseUrl, error.message.orEmpty())
    else -> ConnectionFailure.Unknown(baseUrl, error.message.orEmpty())
}

private fun classifyIoFailure(baseUrl: String, error: Throwable): ConnectionFailure = when (error) {
    is SSLException -> ConnectionFailure.TlsCertificateError(baseUrl, error.message.orEmpty())
    is SocketTimeoutException -> ConnectionFailure.Timeout(baseUrl)
    is ConnectException -> ConnectionFailure.ConnectionRefused(baseUrl, schemeOf(baseUrl))
    is UnknownHostException -> ConnectionFailure.Unknown(baseUrl, "域名无法解析")
    is NoRouteToHostException -> ConnectionFailure.Unknown(baseUrl, "没有到该主机的路由")
    is PortUnreachableException -> ConnectionFailure.ConnectionRefused(baseUrl, schemeOf(baseUrl))
    is IOException -> ConnectionFailure.Unknown(baseUrl, error.message.orEmpty())
    else -> ConnectionFailure.Unknown(baseUrl, error.message.orEmpty())
}

private fun schemeOf(baseUrl: String): String =
    baseUrl.substringBefore("://", missingDelimiterValue = "http").lowercase()
