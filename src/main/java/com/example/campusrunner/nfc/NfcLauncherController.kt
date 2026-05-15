package com.example.campusrunner.nfc

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.campusrunner.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale
import java.util.UUID

class NfcLauncherController(
    private val activity: Activity,
    private val onStateChanged: () -> Unit
) {
    private val prefs: SharedPreferences =
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    val deviceId: String = getOrCreateDeviceId()

    private var nfcDialog: AlertDialog? = null
    private var activationDialog: AlertDialog? = null
    private var lastNfcToastAt = 0L
    @Volatile
    private var activationCheckRunning = false

    val isActivated: Boolean
        get() = prefs.getBoolean(KEY_ACTIVATION_VERIFIED, false)

    val currentLink: String
        get() = getSavedOrDefaultUrl()

    val nfcStatusText: String
        get() = when {
            nfcAdapter == null -> "NFC not supported"
            !nfcAdapter.isEnabled -> "NFC is off"
            else -> "Ready to scan NFC tags"
        }

    fun onCreate() {
        verifyActivationStatus()
    }

    fun onResume() {
        verifyActivationStatus()
        onStateChanged()
    }

    fun onPause() {
        disableNfcReaderMode()
    }

    fun openAlipayLink() {
        if (!ensureActivated()) return

        val url = getSavedOrDefaultUrl()
        if (!hasUsableScheme(url)) {
            toast("链接无效")
            return
        }

        try {
            activity.startActivity(buildNfcIntent(url))
        } catch (e: ActivityNotFoundException) {
            toast("未找到可处理该 NFC 跳转的应用")
        } catch (e: RuntimeException) {
            toast("${e.javaClass.simpleName}: ${e.message.orEmpty()}")
        }
    }

    fun showCurrentLinkDialog() {
        val url = getSavedOrDefaultUrl()
        AlertDialog.Builder(activity)
            .setTitle("当前 NFC 链接")
            .setMessage(url)
            .setPositiveButton("复制") { _, _ -> copyLink(url) }
            .setNegativeButton("关闭", null)
            .show()
    }

    fun saveLink(url: String) {
        if (!ensureActivated()) return

        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_NFC_URL).apply()
            onStateChanged()
            toast("已恢复默认链接")
            return
        }
        if (!hasUsableScheme(trimmed)) {
            toast("链接无效")
            return
        }
        prefs.edit().putString(KEY_NFC_URL, trimmed).apply()
        onStateChanged()
        toast("链接已保存")
    }

    fun showActivationDialog() {
        if (activity.isFinishing || isActivated) return
        if (activationDialog?.isShowing == true) return

        val padding = dp(20)
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
        }
        val message = TextView(activity).apply {
            text = "这台设备还没有绑定。请先通过 Telegram 完成验证，或输入管理员给你的激活码。"
            textSize = 14f
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        val terms = TextView(activity).apply {
            text = """
                使用条款
                1. 本软件仅供技术交流、学习研究与授权测试，请勿用于任何违法违规、破坏平台规则、侵犯第三方权益或影响他人正常服务的行为。
                2. 请在下载、安装或激活后的 24 小时内删除本软件及相关副本；继续保留或使用即视为你已自行承担全部风险。
                3. 使用本软件产生的一切账号、设备、平台、法律或其他后果均由使用者自行承担，与作者、贡献者及相关人员无关。
                4. 点击“激活”或“Telegram 验证”即表示你已阅读、理解并同意以上条款。
            """.trimIndent()
            textSize = 12f
            setTextColor(0xFF64748B.toInt())
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        val deviceView = TextView(activity).apply {
            text = "设备 ID: $deviceId"
            textSize = 12f
            setTextIsSelectable(true)
        }
        val codeInput = EditText(activity).apply {
            hint = "输入验证码或激活码"
            isSingleLine = true
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        container.addView(message)
        container.addView(terms)
        container.addView(deviceView)
        container.addView(codeInput)

        activationDialog = AlertDialog.Builder(activity)
            .setTitle("设备验证")
            .setView(container)
            .setPositiveButton("激活", null)
            .setNegativeButton("Telegram 验证", null)
            .setCancelable(false)
            .create()
            .apply {
                setCanceledOnTouchOutside(false)
            }

        activationDialog?.setOnShowListener {
            activationDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val code = codeInput.text.toString().trim()
                if (code.isEmpty()) {
                    toast("请输入验证码或激活码")
                } else {
                    redeemActivationCode(code)
                }
            }
            activationDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                openTelegramVerification()
            }
        }
        activationDialog?.show()
    }

    fun ensureActivated(): Boolean {
        if (activationCheckRunning) {
            toast("正在校验设备授权...")
            return false
        }
        if (isActivated) return true
        showActivationDialog()
        return false
    }

    private fun enableNfcReaderMode() {
        val adapter = nfcAdapter ?: return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V
        adapter.enableReaderMode(activity, { tag -> handleNfcTag(tag) }, flags, null)
    }

    private fun disableNfcReaderMode() {
        nfcAdapter?.disableReaderMode(activity)
    }

    private fun handleNfcTag(tag: Tag) {
        if (!isActivated) return
        val url = readUrlFromTag(tag)
        activity.runOnUiThread { handleNfcUrl(url) }
    }

    private fun readUrlFromTag(tag: Tag): String? {
        val ndef = Ndef.get(tag) ?: return null
        return try {
            ndef.connect()
            val message = ndef.ndefMessage ?: return null
            extractAlipayUrl(message)
        } catch (_: FormatException) {
            null
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        } finally {
            runCatching { ndef.close() }
        }
    }

    private fun extractAlipayUrl(message: NdefMessage): String? {
        var hasAlipayAar = false
        var firstUrl: String? = null

        message.records.forEach { record ->
            if (isAlipayAar(record)) {
                hasAlipayAar = true
            } else if (firstUrl == null) {
                firstUrl = record.toUri()?.toString()
            }
        }

        val url = firstUrl ?: return null
        if (!isAcceptedNfcUrl(url)) return null
        return if (hasAlipayAar || url.contains("render.alipay.com") || url.startsWith("alipay://")) {
            url
        } else {
            null
        }
    }

    private fun isAlipayAar(record: NdefRecord): Boolean {
        return record.tnf == NdefRecord.TNF_EXTERNAL_TYPE &&
                record.type.contentEquals("android.com:pkg".toByteArray(StandardCharsets.US_ASCII)) &&
                ALIPAY_PACKAGE == String(record.payload, StandardCharsets.UTF_8)
    }

    private fun handleNfcUrl(url: String?) {
        if (url == null) {
            showThrottledToast("未读取到可用的支付宝 NFC 链接")
            return
        }
        if (url == getSavedOrDefaultUrl()) {
            showThrottledToast("当前链接已经是最新")
            return
        }
        showNfcLinkDialog(url)
    }

    private fun showNfcLinkDialog(url: String) {
        if (nfcDialog?.isShowing == true) return

        nfcDialog = AlertDialog.Builder(activity)
            .setTitle("发现 NFC 链接")
            .setMessage(url)
            .setPositiveButton("保存") { _, _ -> saveDiscoveredUrl(url) }
            .setNeutralButton("复制") { _, _ -> copyLink(url) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveDiscoveredUrl(url: String) {
        prefs.edit().putString(KEY_NFC_URL, url).apply()
        onStateChanged()
        toast("新链接已保存")
    }

    private fun buildNfcIntent(url: String): Intent {
        val uri = Uri.parse(url)
        val message = NdefMessage(
            arrayOf(
                NdefRecord.createUri(uri),
                NdefRecord.createApplicationRecord(ALIPAY_PACKAGE)
            )
        )
        return Intent(NfcAdapter.ACTION_NDEF_DISCOVERED, uri).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            setPackage(ALIPAY_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf(message))
        }
    }

    private fun openTelegramVerification() {
        var bot = BuildConfig.TELEGRAM_BOT_USERNAME.trim()
        if (bot.isEmpty() || bot == "your_bot_username") {
            toast("请先在 gradle.properties 配置 telegramBotUsername")
            return
        }
        if (bot.startsWith("@")) {
            bot = bot.drop(1)
        }
        val uri = Uri.parse("https://t.me/$bot?start=${Uri.encode(deviceId)}")
        activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun redeemActivationCode(code: String) {
        toast("正在校验激活码...")
        Thread {
            try {
                val request = JSONObject()
                    .put("deviceId", deviceId)
                    .put("code", code)
                    .put("appVersion", BuildConfig.VERSION_NAME)
                val response = postJson("/api/redeem", request)
                if (response.optBoolean("ok", false)) {
                    markActivated(response.optString("verifiedAt", ""))
                    activity.runOnUiThread {
                        activationDialog?.dismiss()
                        enableNfcReaderMode()
                        onStateChanged()
                        toast("激活成功")
                    }
                } else {
                    val message = response.optString("message", "激活失败，请检查验证码")
                    activity.runOnUiThread { toast(message) }
                }
            } catch (e: Exception) {
                activity.runOnUiThread { toast("网络校验失败: ${readableError(e)}") }
            }
        }.start()
    }

    private fun verifyActivationStatus() {
        if (activationCheckRunning) return
        activationCheckRunning = true
        Thread {
            try {
                val request = JSONObject()
                    .put("deviceId", deviceId)
                    .put("appVersion", BuildConfig.VERSION_NAME)
                val response = postJson("/api/status", request)
                if (response.optBoolean("verified", false)) {
                    markActivated(response.optString("verifiedAt", ""))
                    activity.runOnUiThread {
                        enableNfcReaderMode()
                        activationDialog?.dismiss()
                        onStateChanged()
                    }
                } else {
                    clearActivation()
                    activity.runOnUiThread {
                        disableNfcReaderMode()
                        onStateChanged()
                        showActivationDialog()
                    }
                }
            } catch (e: Exception) {
                clearActivation()
                activity.runOnUiThread {
                    disableNfcReaderMode()
                    onStateChanged()
                    showActivationDialog()
                    toast("网络校验失败: ${readableError(e)}")
                }
            } finally {
                activationCheckRunning = false
            }
        }.start()
    }

    private fun markActivated(verifiedAt: String) {
        prefs.edit()
            .putBoolean(KEY_ACTIVATION_VERIFIED, true)
            .putString(KEY_ACTIVATION_VERIFIED_AT, verifiedAt)
            .apply()
    }

    private fun clearActivation() {
        prefs.edit()
            .remove(KEY_ACTIVATION_VERIFIED)
            .remove(KEY_ACTIVATION_VERIFIED_AT)
            .apply()
    }

    private fun postJson(path: String, body: JSONObject): JSONObject {
        val connection = URL(resolveApiUrl(path)).openConnection() as HttpURLConnection
        connection.connectTimeout = NETWORK_TIMEOUT_MS
        connection.readTimeout = NETWORK_TIMEOUT_MS
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.doOutput = true

        val payload = body.toString().toByteArray(StandardCharsets.UTF_8)
        connection.outputStream.use { output: OutputStream -> output.write(payload) }

        val status = connection.responseCode
        val text = readStream(if (status in 200..399) connection.inputStream else connection.errorStream)
        if (text.isBlank()) {
            throw IOException("HTTP $status")
        }
        return JSONObject(text).also {
            if (status !in 200..399) it.put("httpStatus", status)
        }
    }

    private fun resolveApiUrl(path: String): String {
        var baseUrl = BuildConfig.ACTIVATION_BASE_URL.trim()
        if (baseUrl.isEmpty()) {
            baseUrl = "https://your-worker.example.workers.dev"
        }
        return baseUrl.trimEnd('/') + path
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

    private fun readableError(e: Exception): String {
        return e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
    }

    private fun getOrCreateDeviceId(): String {
        val savedDeviceId = prefs.getString(KEY_DEVICE_ID, "").orEmpty()
        if (savedDeviceId.isNotBlank()) return savedDeviceId

        val androidId = Settings.Secure.getString(activity.contentResolver, Settings.Secure.ANDROID_ID)
        val source = if (androidId.isNullOrBlank()) UUID.randomUUID().toString() else "android:${androidId.trim()}"
        val generated = sha256Hex(source).take(32).uppercase(Locale.US)
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    private fun sha256Hex(value: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.digest(value.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(Locale.US, it) }
        } catch (_: NoSuchAlgorithmException) {
            UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8)).toString().replace("-", "")
        }
    }

    private fun copyLink(url: String) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("NFC 链接", url))
        toast("链接已复制")
    }

    private fun getSavedOrDefaultUrl(): String {
        return prefs.getString(KEY_NFC_URL, "").orEmpty().trim().ifEmpty { DEFAULT_NFC_URL }
    }

    private fun hasUsableScheme(url: String): Boolean {
        return Uri.parse(url).scheme?.isNotBlank() == true
    }

    private fun isAcceptedNfcUrl(url: String): Boolean {
        if (!hasUsableScheme(url)) return false
        val uri = Uri.parse(url)
        val scheme = uri.scheme.orEmpty()
        if (scheme.equals("alipay", ignoreCase = true)) return true
        val host = uri.host
        return (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) &&
                host != null &&
                host.endsWith("alipay.com")
    }

    private fun showThrottledToast(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastNfcToastAt < 1500) return
        lastNfcToastAt = now
        toast(message)
    }

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"
        private const val PREFS_NAME = "launcher_prefs"
        private const val KEY_NFC_URL = "nfc_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ACTIVATION_VERIFIED = "activation_verified"
        private const val KEY_ACTIVATION_VERIFIED_AT = "activation_verified_at"
        private const val NETWORK_TIMEOUT_MS = 12000
        private const val DEFAULT_NFC_URL =
            "https://render.alipay.com/p/s/ulink/qd?s=dc&scheme=alipay%3A%2F%2Fnfc%2Fapp%3Fid%3D20002153%26t%3Dsc04ougx27gc%26p%3DbkMWn3MPU7mzxz796IPA3Ltk7RJTZo37yBt5sB%2Fwpfg%3D"
    }
}
