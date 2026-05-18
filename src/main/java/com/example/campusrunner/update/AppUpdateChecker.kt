package com.example.campusrunner.update

import com.example.campusrunner.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

data class AppUpdateResult(
    val updateRequired: Boolean,
    val latestVersion: String,
    val releaseUrl: String,
    val message: String = ""
)

object AppUpdateChecker {
    private const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/ZekTy/qunide-campus-run/releases/latest"
    private const val GITHUB_RELEASES_URL =
        "https://github.com/ZekTy/qunide-campus-run/releases/latest"
    private const val NETWORK_TIMEOUT_MS = 12000

    fun checkLatest(): AppUpdateResult {
        val connection = URL(GITHUB_LATEST_RELEASE_API).openConnection() as HttpURLConnection
        connection.connectTimeout = NETWORK_TIMEOUT_MS
        connection.readTimeout = NETWORK_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "RouteVerge-Android/${BuildConfig.VERSION_NAME}")

        val status = connection.responseCode
        val text = readStream(if (status in 200..399) connection.inputStream else connection.errorStream)
        if (status !in 200..399 || text.isBlank()) {
            throw IOException("GitHub HTTP $status")
        }

        val json = JSONObject(text)
        val latestVersion = normalizeVersion(
            json.optString("tag_name").ifBlank { json.optString("name") }
        )
        if (latestVersion.isBlank()) {
            throw IOException("无法读取最新版本号")
        }
        val releaseUrl = json.optString("html_url").ifBlank { GITHUB_RELEASES_URL }
        val currentVersion = normalizeVersion(BuildConfig.VERSION_NAME)

        return AppUpdateResult(
            updateRequired = compareVersions(latestVersion, currentVersion) > 0,
            latestVersion = latestVersion,
            releaseUrl = releaseUrl
        )
    }

    private fun normalizeVersion(value: String): String {
        return value
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .lowercase(Locale.US)
            .substringBefore("+")
            .substringBefore("-")
            .trim()
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = versionParts(left)
        val rightParts = versionParts(right)
        val size = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until size) {
            val diff = (leftParts.getOrNull(index) ?: 0) - (rightParts.getOrNull(index) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    private fun versionParts(version: String): List<Int> {
        return version
            .split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    }

    private fun readStream(stream: InputStream?): String {
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            buildString {
                while (true) {
                    val line = reader.readLine() ?: break
                    append(line)
                }
            }
        }
    }
}
