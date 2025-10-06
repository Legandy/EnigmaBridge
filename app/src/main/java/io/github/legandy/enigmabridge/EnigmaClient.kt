@file:OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)

package io.github.legandy.enigmabridge

import android.content.SharedPreferences
import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

@Serializable
data class BouquetResponse(@SerialName("bouquets") val bouquets: List<List<String>>)
@Serializable
data class ServiceResponse(@SerialName("services") val services: List<Service>)
@Serializable
data class Service(@SerialName("servicereference") val sRef: String, @SerialName("servicename") val sName: String)
@Serializable
data class TimerListResponse(@SerialName("timers") val timers: List<Timer>)
@Serializable
data class SimpleResultResponse(
    @SerialName("result") val result: Boolean,
    @SerialName("message") val message: String
)

@Parcelize
@Serializable
data class Timer(
    @SerialName("serviceref") val sRef: String? = null,
    @SerialName("servicename") val sName: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
    @SerialName("begin") val beginTimestamp: Long,
    @SerialName("end") val endTimestamp: Long,
    @SerialName("state") val state: Int,
    @SerialName("justplay") val justPlay: Int,
    @SerialName("afterevent") val afterEvent: Int,
    @SerialName("repeated") val repeated: Int,
    @SerialName("disabled") val disabled: Int,
    // **THE FIX: Make these fields nullable with default values to prevent parsing crash**
    @SerialName("dirname") val directoryName: String? = null,
    @SerialName("tags") val tags: String? = null
) : Parcelable

class EnigmaClient(
    private val ipAddress: String,
    private val user: String,
    private val pass: String,
    private val prefs: SharedPreferences
) {

    private val client = OkHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    companion object {
        private const val TAG = "EnigmaClient"
        private const val API_ABOUT = "/api/about"
        private const val API_TIMER_ADD = "/api/timeradd"
        private const val API_TIMER_DELETE = "/api/timerdelete"
        private const val API_TIMER_CHANGE = "/api/timerchange"
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

    private suspend fun executeAction(url: String): Pair<Boolean, String> {
        val jsonString = executeRequest(url)
        return if (jsonString != null) {
            try {
                val response = json.decodeFromString<SimpleResultResponse>(jsonString)
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
        return executeAction(url)
    }

    suspend fun deleteTimer(timer: Timer): Pair<Boolean, String> {
        val query = "sRef=${timer.sRef}&begin=${timer.beginTimestamp}&end=${timer.endTimestamp}"
        val url = buildUrl(API_TIMER_DELETE, query)
        return executeAction(url)
    }

    suspend fun editTimer(
        originalTimer: Timer,
        newTitle: String,
        newDescription: String,
        newStartTime: Long,
        newEndTime: Long,
        justPlay: Int,
        repeated: Int,
        afterEvent: Int,
        disabled: Int
    ): Pair<Boolean, String> {
        val identificationParams = "channelOld=${originalTimer.sRef}&beginOld=${originalTimer.beginTimestamp}&endOld=${originalTimer.endTimestamp}"

        val query = buildString {
            append("sRef=${originalTimer.sRef}")
            append("&name=${encode(newTitle)}")
            append("&description=${encode(newDescription)}")
            append("&begin=$newStartTime")
            append("&end=$newEndTime")
            append("&disabled=$disabled")
            append("&justplay=$justPlay")
            append("&afterevent=$afterEvent")
            append("&repeated=$repeated")
            append("&dirname=${originalTimer.directoryName ?: ""}")
            append("&tags=${originalTimer.tags ?: ""}")
            append("&always_zap=0")
            append("&$identificationParams")
        }
        val url = buildUrl(API_TIMER_CHANGE, query)
        return executeAction(url)
    }

    suspend fun checkConnection(): Boolean = executeRequest(buildUrl(API_ABOUT)) != null

    suspend fun getBouquets(): Map<String, String>? {
        val url = buildUrl(API_BOUQUETS, "stype=1")
        val jsonString = executeRequest(url)
        return jsonString?.let {
            try { json.decodeFromString<BouquetResponse>(it).bouquets.associate { b -> b[1] to b[0] } }
            catch (e: Exception) { Log.e(TAG, "Failed to parse bouquets JSON.", e); null }
        }
    }

    suspend fun getChannelsInBouquet(bouquetSref: String): Map<String, String>? {
        val cleanedSref = bouquetSref.replace("\\\"", "\"")
        val useHttps = prefs.getBoolean("USE_HTTPS", false)
        val scheme = if (useHttps) "https" else "http"
        val url = "$scheme://$ipAddress$API_GET_SERVICES?sRef=$cleanedSref"

        val jsonString = executeRequest(url)
        return jsonString?.let {
            try { json.decodeFromString<ServiceResponse>(it).services.associate { s -> s.sName to s.sRef } }
            catch (e: Exception) { Log.e(TAG, "Failed to parse channels JSON.", e); null }
        }
    }

    suspend fun getTimerList(): List<Timer>? {
        val jsonString = executeRequest(buildUrl(API_TIMER_LIST))
        return jsonString?.let {
            try { json.decodeFromString<TimerListResponse>(it).timers }
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
            if (response.isSuccessful) { responseBody } else {
                Log.e(TAG, "Request failed for URL '$url': Code=${response.code}, Message=${response.message}, Body=$responseBody")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "IOException during request for URL: $url", e); null
        }
    }
}

