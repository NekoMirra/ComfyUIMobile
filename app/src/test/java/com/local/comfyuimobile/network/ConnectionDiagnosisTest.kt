package com.local.comfyuimobile.network

import java.net.ConnectException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionDiagnosisTest {
    private val address = "http://192.168.1.20:8188"

    @Test fun invalidAddressExplainsTheSchemeAndRootPath() {
        val diagnosis = diagnose(address, ConnectionFailure.InvalidAddress(address, "端口无效"))

        assertEquals(DiagnosisKind.INVALID_ADDRESS, diagnosis.diagnosis)
        assertTrue(diagnosis.title.contains("地址格式不正确"))
        assertTrue(diagnosis.detail.contains("端口无效"))
        assertTrue(diagnosis.actions.any { it.contains("改用 http:// 开头") })
        assertTrue(diagnosis.actions.any { it.contains("根地址") })
    }

    @Test fun refusedHttpPortSuggestsTheListenFlag() {
        val diagnosis = diagnose(address, ConnectionFailure.ConnectionRefused(address, "http"))

        assertEquals(DiagnosisKind.CONNECTION_REFUSED, diagnosis.diagnosis)
        assertTrue(diagnosis.title.contains("无法连接到服务器端口"))
        assertTrue(diagnosis.detail.contains("拒绝连接"))
        assertTrue(diagnosis.actions.any { it.contains("--listen 0.0.0.0") })
        assertTrue(diagnosis.actions.any { it.contains("http://") })
    }

    @Test fun refusedHttpsPortSuggestsTheHttpsScheme() {
        val diagnosis = diagnose("https://cloud.example.com:443", ConnectionFailure.ConnectionRefused("https://cloud.example.com:443", "https"))

        assertEquals(DiagnosisKind.CONNECTION_REFUSED, diagnosis.diagnosis)
        assertTrue(diagnosis.title.contains("HTTPS"))
        assertTrue(diagnosis.detail.contains("HTTPS"))
        assertTrue(diagnosis.actions.any { it.contains("https://") })
    }

    @Test fun tlsProblemExplainsTheCertificate() {
        val diagnosis = diagnose(address, ConnectionFailure.TlsCertificateError(address, "证书已过期"))

        assertEquals(DiagnosisKind.TLS_CERTIFICATE, diagnosis.diagnosis)
        assertTrue(diagnosis.title.contains("证书校验失败"))
        assertTrue(diagnosis.detail.contains("证书已过期"))
        assertTrue(diagnosis.actions.any { it.contains("http:// 直连地址") })
    }

    @Test fun htmlLoginPageExplainsThatTheAppNeedsAFullInstance() {
        val diagnosis = diagnose(address, ConnectionFailure.HtmlLoginPage(address))

        assertEquals(DiagnosisKind.HTML_LOGIN_PAGE, diagnosis.diagnosis)
        assertTrue(diagnosis.title.contains("平台登录页"))
        assertTrue(diagnosis.actions.any { it.contains("本 App 需要自带前端的完整实例") })
        assertTrue(diagnosis.actions.any { it.contains("system_stats") })
    }

    @Test fun outdatedFrontendPointsAtTheFrontendPackage() {
        val diagnosis = diagnose(address, ConnectionFailure.FrontendTooOld(address, "1.57.0", "1.56.0"))

        assertEquals(DiagnosisKind.FRONTEND_TOO_OLD, diagnosis.diagnosis)
        assertTrue(diagnosis.title.contains("前端版本过低"))
        assertTrue(diagnosis.detail.contains("1.57.0"))
        assertTrue(diagnosis.detail.contains("1.56.0"))
        assertTrue(diagnosis.actions.any { it.contains("comfyui-frontend-package") })
    }

    @Test fun missingFrontendIsReportedSeparately() {
        val diagnosis = diagnose(address, ConnectionFailure.FrontendMissing(address))

        assertEquals(DiagnosisKind.FRONTEND_MISSING, diagnosis.diagnosis)
        assertTrue(diagnosis.title.contains("缺少前端"))
        assertTrue(diagnosis.actions.any { it.contains("comfyui-frontend-package") })
    }

    @Test fun notComfyUiKeepsTheStatusCodeInTheDetail() {
        val diagnosis = diagnose(address, ConnectionFailure.NotComfyUi(address, 404))

        assertEquals(DiagnosisKind.NOT_COMFYUI, diagnosis.diagnosis)
        assertTrue(diagnosis.title.contains("不是 ComfyUI"))
        assertTrue(diagnosis.detail.contains("404"))
        assertTrue(diagnosis.actions.any { it.contains("system_stats") })
    }

    @Test fun timeoutExplainsThatTheServerDidNotAnswer() {
        val diagnosis = diagnose(address, ConnectionFailure.Timeout(address, 5_000L))

        assertEquals(DiagnosisKind.TIMEOUT, diagnosis.diagnosis)
        assertTrue(diagnosis.title.contains("超时"))
        assertTrue(diagnosis.detail.contains("5 秒"))
        assertTrue(diagnosis.actions.any { it.contains("--listen 0.0.0.0") })
    }

    @Test fun unknownFailureStillGivesAdvice() {
        val diagnosis = diagnose(address, ConnectionFailure.Unknown(address, "socket closed"))

        assertEquals(DiagnosisKind.UNKNOWN, diagnosis.diagnosis)
        assertTrue(diagnosis.title.contains("无法连接到服务器"))
        assertTrue(diagnosis.detail.contains("socket closed"))
        assertTrue(diagnosis.actions.isNotEmpty())
    }

    @Test fun everyFailureKindProducesChineseTextAndActions() {
        val failures = listOf(
            ConnectionFailure.InvalidAddress(address),
            ConnectionFailure.ConnectionRefused(address, "http"),
            ConnectionFailure.ConnectionRefused(address, "https"),
            ConnectionFailure.TlsCertificateError(address),
            ConnectionFailure.HtmlLoginPage(address),
            ConnectionFailure.FrontendTooOld(address, "1.57.0", "1.56.0"),
            ConnectionFailure.FrontendMissing(address),
            ConnectionFailure.NotComfyUi(address),
            ConnectionFailure.Timeout(address),
            ConnectionFailure.Unknown(address),
        )

        failures.forEach { failure ->
            val diagnosis = diagnose(address, failure)
            assertTrue("标题必须包含中文：$diagnosis", diagnosis.title.any { it.code > 0x7F })
            assertTrue("详情必须包含中文：$diagnosis", diagnosis.detail.any { it.code > 0x7F })
            assertTrue("必须给出建议：$diagnosis", diagnosis.actions.isNotEmpty())
            assertTrue("建议必须包含中文：$diagnosis", diagnosis.actions.all { it.any { character -> character.code > 0x7F } })
        }
    }

    @Test fun exceptionsAreMappedToTheMatchingFailureKind() {
        assertTrue(classifyFailure(address, IllegalArgumentException("端口无效")) is ConnectionFailure.InvalidAddress)
        assertTrue(classifyFailure(address, ConnectException("refused")) is ConnectionFailure.ConnectionRefused)
        assertTrue(classifyFailure(address, ServerTlsException("证书错误")) is ConnectionFailure.TlsCertificateError)
        assertTrue(classifyFailure(address, ServerTimeoutException("超时")) is ConnectionFailure.Timeout)
        assertTrue(classifyFailure(address, ServerUnreachableException("不可达")) is ConnectionFailure.Unknown)
        assertTrue(classifyFailure(address, ServerUnreachableException("不可达", ConnectException("refused"))) is ConnectionFailure.ConnectionRefused)
        assertTrue(classifyFailure(address, UnsupportedEndpointException("/features", 404, "不支持")) is ConnectionFailure.NotComfyUi)
        assertTrue(classifyFailure(address, MalformedResponseException("/system_stats", "网页")) is ConnectionFailure.HtmlLoginPage)
    }
}
