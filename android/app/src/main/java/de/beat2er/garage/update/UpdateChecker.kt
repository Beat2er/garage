package de.beat2er.garage.update

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import de.beat2er.garage.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class VersionInfo(
    @SerializedName("versionCode") val versionCode: Int = 0,
    @SerializedName("versionName") val versionName: String = "",
    @SerializedName("apkUrl") val apkUrl: String = "",
    @SerializedName("changelog") val changelog: String = ""
)

data class UpdateInfo(
    val available: Boolean,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String
)

object UpdateChecker {

    private const val BASE_URL = "https://beat2er.github.io/garage/app/"

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(BuildConfig.UPDATE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"

            if (connection.responseCode != 200) return@withContext null

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val remote = Gson().fromJson(json, VersionInfo::class.java)

            if (isNewer(remote.versionName, BuildConfig.VERSION_NAME)) {
                val downloadUrl = if (remote.apkUrl.startsWith("http")) {
                    remote.apkUrl
                } else {
                    BASE_URL + remote.apkUrl
                }
                UpdateInfo(
                    available = true,
                    versionName = remote.versionName,
                    downloadUrl = downloadUrl,
                    changelog = remote.changelog
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
