package io.github.legandy.enigmabridge

import android.content.SharedPreferences
import android.os.Parcelable
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

// --- Data classes for JSON parsing ---
data class BouquetResponse(@SerializedName("bouquets") val bouquets: List<List<String>>)
data class ServiceResponse(@SerializedName("services") val services: List<Service>)
data class Service(@SerializedName("servicereference") val sRef: String, @SerializedName("servicename") val sName: String)
data class TimerListResponse(@SerializedName("timers") val timers: List<Timer>)
data class SimpleResultResponse(
    @SerializedName("result") val result: Boolean,
    @SerializedName("message") val message: String
)

@Parcelize
data class Timer(
    @SerializedName("servicereference") val sRef: String?,
    @SerializedName("servicename") val sName: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("begin") val beginTimestamp: Long,
    @SerializedName("end") val endTimestamp: Long,
    @SerializedName("state") val state: Int,
    @SerializedName("justplay") val justPlay: Int,
    @SerializedName("afterevent") val afterEvent: Int,
    @SerializedName("repeated") val repeated: Int,
    @SerializedName("disabled") val disabled: Int
) : Parcelable

class EnigmaClient(
    private val ipAddress: String,
    private val user: String,
    private val pass: String,
    private val prefs: SharedPreferences
) {

    private val client = OkHttpClient()
    private val gson = Gson()

    companion object {
        private const val TAG = "EnigmaClient"
        private const val API_ABOUT = "/api/about"
        private const val API_TIMER_ADD = "/api/timeradd"
        private const val API_TIMER_DELETE = "/api/timerdelete"
        private const val API_BOUQUETS = "/api/bouquets"
        private const val API_GET_SERVICES = "/api/getservices"
        private const val API_TIMER_LIST = "/api/timerlist"
    }

    private fun buildUrl(path: String, query: String? = null): String {
        val useHttps = prefs.getBoolean("USE_HTTPS", false)
        val scheme = if (useHttps) "https" else "http"
        return "$scheme://$ipAddress$path".let { if (query != null) "$it?$query" else it }
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

    suspend fun checkConnection(): Boolean = executeRequest(buildUrl(API_ABOUT)) != null

    private suspend fun executeAction(url: String): Pair<Boolean, String> {
        val jsonString = executeRequest(url)
        return if (jsonString != null) {
            try {
                val response = gson.fromJson(jsonString, SimpleResultResponse::class.java)
                if (!response.result) {
                    Log.e(TAG, "API action failed. Receiver message: ${response.message}")
                }
                Pair(response.result, response.message)
            } catch (e: Exception) {
                Log.w(TAG, "Could not parse SimpleResultResponse. Assuming success.", e)
                Pair(true, "Success (assumed)")
            }
        } else {
            Pair(false, "Network request failed")
        }
    }

    suspend fun addTimer(
        title: String, sRef: String, startTime: Long, endTime: Long, description: String,
        justPlay: Int, repeated: Int, afterEvent: Int
    ): Pair<Boolean, String> {
        val query = buildString {
            append("sRef=$sRef")
            append("&name=${encode(title)}")
            append("&description=${encode(description)}")
            append("&begin=$startTime")
            append("&end=$endTime")
            append("&disabled=0")
            append("&justplay=$justPlay")
            append("&afterevent=$afterEvent")
            append("&repeated=$repeated")
            append("&always_zap=0")
        }
        val url = buildUrl(API_TIMER_ADD, query)
        Log.d(TAG, "Attempting to add timer with URL: $url")
        return executeAction(url)
    }

    suspend fun deleteTimer(timer: Timer): Pair<Boolean, String> {
        val sRefToDelete = timer.sRef ?: ""

        val query = buildString {
            append("sRef=$sRefToDelete")
            append("&begin=${timer.beginTimestamp}")
            append("&end=${timer.endTimestamp}")
        }

        val url = buildUrl(API_TIMER_DELETE, query)
        Log.d(TAG, "Attempting timer deletion with URL: $url")
        return executeAction(url)
    }

    suspend fun getBouquets(): Map<String, String>? {
        val url = buildUrl(API_BOUQUETS, "stype=1")
        val jsonString = executeRequest(url)
        return jsonString?.let {
            try { gson.fromJson(it, BouquetResponse::class.java).bouquets.associate { b -> b[1] to b[0] } }
            catch (e: Exception) { Log.e(TAG, "Failed to parse bouquets JSON.", e); null }
        }
    }
    suspend fun getChannelsInBouquet(bouquetSref: String): Map<String, String>? {
        val url = buildUrl(API_GET_SERVICES, "sRef=$bouquetSref")
        val jsonString = executeRequest(url)
        return jsonString?.let {
            try { gson.fromJson(it, ServiceResponse::class.java).services.associate { s -> s.sName to s.sRef } }
            catch (e: Exception) { Log.e(TAG, "Failed to parse channels JSON.", e); null }
        }
    }
    suspend fun getTimerList(): List<Timer>? {
        val jsonString = executeRequest(buildUrl(API_TIMER_LIST))
        return jsonString?.let {
            try { gson.fromJson(it, TimerListResponse::class.java).timers }
            catch (e: Exception) { Log.e(TAG, "Failed to parse timer list JSON.", e); null }
        }
    }

    private fun executeRequest(url: String): String? {
        val requestBuilder = Request.Builder().url(url)
        if (user.isNotEmpty() || pass.isNotEmpty()) {
            requestBuilder.header("Authorization", Credentials.basic(user, pass))
        }
        return try {
            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string()
            Log.d(TAG, "Full response for URL '$url': Code=${response.code}, Body=$responseBody")
            if (response.isSuccessful) { responseBody } else { null }
        } catch (e: IOException) {
            Log.e(TAG, "IOException during request for URL: $url", e); null
        }
    }
}

