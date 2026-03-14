@file:OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)

package io.github.legandy.enigmabridge.data

import android.os.Parcelable
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
import javax.net.ssl.SSLHandshakeException

// Manager for communicating with Enigma2 devices
@Serializable
data class BouquetResponse(@SerialName("bouquets") val bouquets: List<List<String>>)

@Serializable
data class ServiceResponse(@SerialName("services") val services: List<Service>)

@Serializable
data class Service(
    @SerialName("servicereference") val sRef: String,
    @SerialName("servicename") val sName: String
)

@Serializable
data class TimerListResponse(@SerialName("timers") val timers: List<Timer>)

@Serializable
data class SimpleResultResponse(
    @SerialName("result") val result: Boolean, @SerialName("message") val message: String
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
    @SerialName("dirname") val directoryName: String? = null,
    @SerialName("tags") val tags: String? = null
) : Parcelable

sealed class ConnectionResult {
    object Success : ConnectionResult()
    data class Failure(val error: String, val isSslIssue: Boolean = false) : ConnectionResult()
}

class EnigmaClient(
    private val ipAddress: String,
    private val user: String,
    private val pass: String,
    private val useHttps: Boolean,
) {

    private val client = OkHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    companion object {
        private const val API_ABOUT = "/api/about"
        private const val API_TIMER_ADD = "/api/timeradd"
        private const val API_TIMER_DELETE = "/api/timerdelete"
        private const val API_TIMER_CHANGE = "/api/timerchange"
        private const val API_BOUQUETS = "/api/bouquets"
        private const val API_GET_SERVICES = "/api/getservices"
        private const val API_TIMER_LIST = "/api/timerlist"
    }

    private fun buildUrl(path: String, query: String? = null): String {
        val scheme = if (useHttps) "https" else "http"
        return "$scheme://$ipAddress$path".let { if (query != null) "$it?$query" else it }
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

    private fun executeAction(url: String): String {
        val jsonString = executeRequest(url)
        val response = json.decodeFromString<SimpleResultResponse>(jsonString)
        if (!response.result) throw Exception(response.message)
        return response.message
    }

    fun addTimer(
        title: String,
        sRef: String,
        startTime: Long,
        endTime: Long,
        description: String,
        justPlay: Int,
        repeated: Int,
        afterEvent: Int
    ): String {
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

    fun deleteTimer(timer: Timer): String {
        val query = "sRef=${timer.sRef}&begin=${timer.beginTimestamp}&end=${timer.endTimestamp}"
        val url = buildUrl(API_TIMER_DELETE, query)
        return executeAction(url)
    }

    fun editTimer(
        originalTimer: Timer,
        newTitle: String,
        newDescription: String,
        newStartTime: Long,
        newEndTime: Long,
        justPlay: Int,
        repeated: Int,
        afterEvent: Int,
        disabled: Int
    ): String {
        val identificationParams =
            "channelOld=${originalTimer.sRef}&beginOld=${originalTimer.beginTimestamp}&endOld=${originalTimer.endTimestamp}"

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

    fun checkConnection(): ConnectionResult {
        return try {
            executeRequest(buildUrl(API_ABOUT))
            ConnectionResult.Success
        } catch (_: SSLHandshakeException) {
            ConnectionResult.Failure("SSL Error: Untrusted Certificate", true)
        } catch (e: Exception) {
            ConnectionResult.Failure(e.localizedMessage ?: "Connection Failed")
        }
    }

    fun getBouquets(): Map<String, String> {
        val url = buildUrl(API_BOUQUETS, "stype=1")
        val jsonString = executeRequest(url)
        return json.decodeFromString<BouquetResponse>(jsonString).bouquets.associate { b -> b[1] to b[0] }
    }

    fun getChannelsInBouquet(bouquetSref: String): Map<String, String> {
        val cleanedSref = bouquetSref.replace("\\\"", "\"")
        val url = buildUrl(API_GET_SERVICES, "sRef=$cleanedSref")

        val jsonString = executeRequest(url)
        return json.decodeFromString<ServiceResponse>(jsonString).services.associate { s -> s.sName to s.sRef }
    }

    fun getTimerList(): List<Timer> {
        val jsonString = executeRequest(buildUrl(API_TIMER_LIST))
        return json.decodeFromString<TimerListResponse>(jsonString).timers
    }

    private fun executeRequest(url: String): String {
        val request = Request.Builder().url(url).apply {
            if (user.isNotEmpty() || pass.isNotEmpty()) {
                header("Authorization", Credentials.basic(user, pass))
            }
        }.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            return response.body.string()
        }
    }
}
