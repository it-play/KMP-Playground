package com.amond.kmpbook.launcher

import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

internal class BoundedHttpsClient {
    private val client = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun getBytes(uri: URI, maximumBytes: Int): ByteArray {
        val response = open(uri, METADATA_TIMEOUT)
        return response.body().use { input ->
            val declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
            if (declaredLength > maximumBytes) {
                throw LauncherException("http-body-size", "배포 메타데이터가 허용 크기를 초과합니다.")
            }
            input.readNBytes(maximumBytes + 1).also { bytes ->
                if (bytes.size > maximumBytes || (declaredLength >= 0 && bytes.size.toLong() != declaredLength)) {
                    throw LauncherException("http-body-size", "배포 메타데이터 크기가 응답과 일치하지 않습니다.")
                }
            }
        }
    }

    fun openArtifact(uri: URI): HttpResponse<InputStream> = open(uri, ARTIFACT_TIMEOUT)

    private fun open(initialUri: URI, timeout: Duration): HttpResponse<InputStream> {
        var current = requireHttps(initialUri)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val request = HttpRequest.newBuilder(current)
                .timeout(timeout)
                .header("Accept-Encoding", "identity")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build()
            val response = try {
                client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            } catch (error: Exception) {
                throw LauncherException("network-failed", "배포 서버에 연결하지 못했습니다.", error)
            }
            val status = response.statusCode()
            if (status == 200) {
                val contentEncoding = response.headers().firstValue("Content-Encoding").orElse("identity")
                if (!contentEncoding.equals("identity", ignoreCase = true)) {
                    response.body().close()
                    throw LauncherException("http-encoding", "배포 서버가 지원하지 않는 압축 응답을 보냈습니다.")
                }
                return response
            }
            if (status !in REDIRECT_STATUSES || redirectCount == MAX_REDIRECTS) {
                response.body().close()
                throw LauncherException("http-status", "배포 서버가 허용되지 않는 HTTP 상태를 반환했습니다: $status")
            }
            val location = response.headers().allValues("Location").singleOrNull()
            response.body().close()
            if (location.isNullOrBlank()) {
                throw LauncherException("http-redirect", "배포 서버 redirect 위치가 올바르지 않습니다.")
            }
            current = try {
                requireHttps(current.resolve(location))
            } catch (error: IllegalArgumentException) {
                throw LauncherException("http-redirect", "배포 서버 redirect URL이 올바르지 않습니다.", error)
            }
        }
        throw LauncherException("http-redirect", "배포 서버 redirect 제한을 초과했습니다.")
    }

    private fun requireHttps(uri: URI): URI {
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() ||
            uri.userInfo != null || uri.fragment != null
        ) {
            throw LauncherException("https-required", "배포 연결은 유효한 HTTPS URL만 사용할 수 있습니다.")
        }
        return uri
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val USER_AGENT = "MarketLedger2040-Launcher/1"
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        val METADATA_TIMEOUT: Duration = Duration.ofSeconds(45)
        val ARTIFACT_TIMEOUT: Duration = Duration.ofMinutes(15)
        val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
    }
}
