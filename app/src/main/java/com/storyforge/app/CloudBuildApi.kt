package com.storyforge.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.util.UUID

internal data class CloudBuildResult(
    val jobId: String,
    val status: String,
    val artifactAvailable: Boolean = false,
    val downloadUrl: String? = null
)

internal fun parseCloudBuildVersionCode(raw: String): Int {
    val parsed = raw.toIntOrNull()
        ?: throw IllegalArgumentException("رقم الإصدار يجب أن يكون عددًا صحيحًا موجبًا.")
    require(parsed in 1..2_100_000_000) {
        "رقم الإصدار يجب أن يكون بين 1 و 2,100,000,000."
    }
    return parsed
}

internal fun validateCloudBuildInputs(
    gameTitle: String,
    applicationId: String,
    versionName: String,
    versionCode: String,
    buildType: String
) {
    val title = gameTitle.trim()
    require(title.isNotEmpty() && title.length <= 40) {
        "اسم اللعبة مطلوب وبحد أقصى 40 حرفًا."
    }
    val packageId = applicationId.trim()
    require(packageId.isEmpty() ||
        Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\\\.[a-zA-Z][a-zA-Z0-9_]*){1,5}$").matches(packageId)
    ) { "معرّف تطبيق Android غير صالح." }
    require(Regex("^\\\\d+\\\\.\\\\d+\\\\.\\\\d+$").matches(versionName.trim())) {
        "اسم الإصدار يجب أن يكون بصيغة MAJOR.MINOR.PATCH مثل 1.0.0."
    }
    parseCloudBuildVersionCode(versionCode.trim())
    require(buildType == "debug" || buildType == "release") {
        "نوع البناء يجب أن يكون debug أو release."
    }
}

internal object CloudBuildApi {
    private fun request(
        baseUrl: String,
        accessToken: String,
        path: String,
        method: String,
        body: JSONObject? = null
    ): JSONObject {
        val base = baseUrl.trim().trimEnd('/')
        require(base.startsWith("https://")) { "رابط الخدمة يجب أن يبدأ بـ HTTPS." }
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Idempotency-Key", UUID.randomUUID().toString().replace("-", ""))
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = if (response.isBlank()) JSONObject() else JSONObject(response)
            if (code !in 200..299) throw IllegalStateException(json.optString("message", json.optString("error", "فشل طلب خدمة البناء ($code).")))
            json
        } finally {
            connection.disconnect()
        }
    }

    fun start(
        baseUrl: String,
        token: String,
        storygameUrl: String,
        gameTitle: String,
        applicationId: String,
        versionName: String,
        versionCode: String,
        buildType: String
    ): CloudBuildResult {
        validateCloudBuildInputs(gameTitle, applicationId, versionName, versionCode, buildType)
        val body = JSONObject()
            .put("storygameUrl", storygameUrl)
            .put("gameTitle", gameTitle.trim())
            .put("versionName", versionName.trim())
            .put("versionCode", parseCloudBuildVersionCode(versionCode.trim()))
            .put("buildType", buildType)
        if (applicationId.isNotBlank()) body.put("applicationId", applicationId.trim())
        val json = request(baseUrl, token, "/api/build-jobs", "POST", body)
        return CloudBuildResult(json.getString("jobId"), json.optString("status", "queued"))
    }

    fun status(baseUrl: String, token: String, jobId: String): CloudBuildResult {
        val json = request(baseUrl, token, "/api/build-jobs/$jobId", "GET")
        return CloudBuildResult(
            jobId = json.optString("jobId", jobId),
            status = json.optString("status", "queued"),
            artifactAvailable = json.optBoolean("artifactAvailable", false),
            downloadUrl = json.optString("downloadUrl").takeIf { it.isNotBlank() && it != "null" }
        )
    }
    fun uploadStoryPackage(baseUrl: String, token: String, file: File): String {
        require(file.isFile && file.length() in 1..(100L * 1024L * 1024L)) { "ملف القصة غير موجود أو يتجاوز 100 ميجابايت." }
        val init = request(
            baseUrl, token, "/api/uploads", "POST",
            JSONObject().put("fileName", file.name).put("fileSize", file.length())
        )
        val uploadUrl = init.getString("uploadUrl")
        val signed = URL(uploadUrl)
        require(signed.protocol == "https:") { "رابط رفع الملف غير آمن." }
        val connection = (signed.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 20000
            readTimeout = 120000
            setFixedLengthStreamingMode(file.length())
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        try {
            file.inputStream().buffered().use { input ->
                connection.outputStream.buffered().use { output -> input.copyTo(output) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("فشل رفع ملف القصة إلى التخزين السحابي ($code).")
        } finally {
            connection.disconnect()
        }
        return init.getString("storygameUrl")
    }

    fun artifactDownloadUrl(baseUrl: String, token: String, jobId: String): String {
        val base = baseUrl.trim().trimEnd('/')
        require(base.startsWith("https://")) { "رابط الخدمة يجب أن يبدأ بـ HTTPS." }
        val connection = (URL(base + "/api/build-jobs/$jobId/artifact").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            if (code !in 300..399) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("تعذر تجهيز التنزيل ($code): $message")
            }
            val location = connection.getHeaderField("Location") ?: error("لم يُرجع الخادم رابط تنزيل.")
            val signed = URL(location)
            require(signed.protocol == "https:" && signed.host.isNotBlank()) { "رابط التنزيل غير آمن." }
            signed.toString()
        } finally { connection.disconnect() }
    }
}
