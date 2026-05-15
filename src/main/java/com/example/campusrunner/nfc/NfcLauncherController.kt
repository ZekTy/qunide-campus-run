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
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
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
        var hasReadAgreement = false
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
            text = SOFTWARE_AGREEMENT
            textSize = 12f
            setTextColor(0xFF64748B.toInt())
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        val agreementCheck = CheckBox(activity).apply {
            text = "请先完整阅读协议"
            textSize = 13f
            isEnabled = false
            setPadding(0, dp(8), 0, dp(4))
        }
        val termsScroll = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(260)
            )
            addView(
                terms,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
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
        container.addView(termsScroll)
        container.addView(agreementCheck)
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
            val activateButton = activationDialog?.getButton(AlertDialog.BUTTON_POSITIVE)
            val telegramButton = activationDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)
            fun updateActionButtons() {
                val canContinue = agreementCheck.isChecked
                activateButton?.isEnabled = canContinue
                telegramButton?.isEnabled = canContinue
            }
            fun markAgreementReadIfNeeded() {
                val child = termsScroll.getChildAt(0) ?: return
                val reachedBottom = termsScroll.scrollY + termsScroll.height >= child.height - dp(4)
                if (reachedBottom && !hasReadAgreement) {
                    hasReadAgreement = true
                    agreementCheck.isEnabled = true
                    agreementCheck.text = "我已完整阅读并同意《软件使用协议与免责声明》"
                }
            }
            termsScroll.setOnScrollChangeListener { _, _, _, _, _ -> markAgreementReadIfNeeded() }
            termsScroll.post { markAgreementReadIfNeeded() }
            agreementCheck.setOnCheckedChangeListener { _, _ -> updateActionButtons() }
            updateActionButtons()

            activateButton?.setOnClickListener {
                if (!agreementCheck.isChecked) {
                    toast("请先完整阅读并勾选同意协议")
                    return@setOnClickListener
                }
                val code = codeInput.text.toString().trim()
                if (code.isEmpty()) {
                    toast("请输入验证码或激活码")
                } else {
                    redeemActivationCode(code)
                }
            }
            telegramButton?.setOnClickListener {
                if (!agreementCheck.isChecked) {
                    toast("请先完整阅读并勾选同意协议")
                    return@setOnClickListener
                }
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
        fun hasExistingInstallState(context: Context): Boolean {
            return context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .all
                .isNotEmpty()
        }

        val SOFTWARE_AGREEMENT = """
            软件使用协议与免责声明

            欢迎使用本软件。请用户在使用前仔细阅读并充分理解本协议内容。用户安装、启动、购买激活码或使用本软件，即视为已阅读、理解并同意本协议。

            1. 软件性质

            本软件为通用型位置模拟、轨迹规划、轨迹保存与轨迹回放工具，主要用于开发测试、地图应用调试、定位功能验证、个人学习研究等合法合规场景。

            本软件不属于任何学校、运动平台、考勤平台、校园服务平台或第三方应用的官方工具，也未获得上述机构或平台的授权、认可或合作。

            2. 合法合规使用

            用户承诺仅在法律法规允许、第三方平台规则允许、且不损害他人或机构合法权益的范围内使用本软件。

            用户不得将本软件用于以下用途，包括但不限于：

            1. 伪造运动轨迹、运动成绩、校园跑数据、体育考核数据；
            2. 规避学校、单位、平台的签到、考勤、考核、风控或管理规则；
            3. 作弊、刷分、刷课、刷任务、刷活动、刷奖励；
            4. 欺骗学校、教师、单位、平台或其他第三方；
            5. 违反任何第三方应用、平台或服务的用户协议、管理规定或技术限制；
            6. 侵犯他人隐私、扰乱平台正常运行或实施其他违法违规行为。

            如用户将本软件用于上述用途，相关责任由用户自行承担。

            3. 第三方平台责任

            本软件仅提供本地位置模拟及轨迹管理功能，不提供第三方平台账号登录、数据上传、接口调用、破解、绕过验证、篡改数据等服务。

            用户在第三方平台中的任何行为，包括但不限于登录、打卡、上传轨迹、提交数据、获取成绩或奖励等，均属于用户自行操作。用户应自行遵守相关平台规则、学校规定、单位制度及法律法规。

            因用户违反第三方平台规则、学校规定或法律法规而导致的账号封禁、成绩取消、纪律处分、经济损失或其他后果，由用户自行承担。

            4. 禁止误用说明

            本软件不鼓励、不支持、不帮助任何形式的作弊、虚假打卡、虚假运动、虚假定位或规避管理行为。

            如开发者发现用户将本软件用于违法违规、作弊、欺诈或损害第三方权益的行为，开发者有权停止提供服务、取消激活资格、拒绝继续授权，并视情况保留进一步处理的权利。

            5. 激活码与费用说明

            用户购买的激活码仅用于解锁本软件的通用功能。激活码不代表开发者承诺用户能够在任何第三方平台中实现特定效果，也不代表开发者保证用户可通过本软件完成某项考勤、运动、签到、考核或平台任务。

            因第三方平台规则调整、系统检测、设备限制、用户操作不当或其他不可控因素导致软件无法达到用户预期效果的，开发者不承担由此产生的间接损失。

            6. 数据与隐私

            本软件不主动收集用户的第三方平台账号、密码、学号、身份证号、手机号等敏感信息。

            如软件需要保存用户自定义轨迹、配置文件或激活状态，相关数据仅用于软件本身功能。开发者应尽合理努力保护用户数据安全。

            用户不得向本软件提交他人隐私信息或未经授权的数据。

            7. 风险提示

            用户理解并同意，位置模拟功能可能被部分应用或平台识别为异常行为。用户使用本软件可能导致第三方应用功能受限、账号异常、数据无效或其他后果。

            用户应自行判断使用场景的合法性、合规性及风险。

            8. 免责声明

            在法律法规允许的范围内，开发者不对用户因违反法律法规、学校规定、单位制度、第三方平台协议或本协议而产生的任何直接或间接后果承担责任。

            但本免责声明不排除法律规定不得排除或限制的责任。

            9. 协议变更与终止

            开发者有权根据法律法规、软件功能变化或合规要求更新本协议。用户继续使用本软件，视为接受更新后的协议。

            如用户不同意本协议或后续更新内容，应立即停止使用本软件。
        """.trimIndent()
        private const val DEFAULT_NFC_URL =
            "https://render.alipay.com/p/s/ulink/qd?s=dc&scheme=alipay%3A%2F%2Fnfc%2Fapp%3Fid%3D20002153%26t%3Dsc04ougx27gc%26p%3DbkMWn3MPU7mzxz796IPA3Ltk7RJTZo37yBt5sB%2Fwpfg%3D"
    }
}
