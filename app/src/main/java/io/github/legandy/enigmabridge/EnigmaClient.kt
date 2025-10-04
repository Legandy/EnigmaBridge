package io.github.legandy.enigmabridge

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

class EnigmaClient(private val ipAddress: String, private val user: String, private val pass: String) {

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
        return "http://$ipAddress$path".let { if (query != null) "$it?$query" else it }
    }

    suspend fun checkConnection(): Boolean {
        return executeRequest(buildUrl(API_ABOUT)) != null
    }

    private suspend fun executeAction(url: String): Boolean {
        val jsonString = executeRequest(url)
        return if (jsonString != null) {
            try {
                val response = gson.fromJson(jsonString, SimpleResultResponse::class.java)
                if (!response.result) {
                    Log.e(TAG, "API action failed. Receiver message: ${response.message}")
                }
                response.result
            } catch (e: Exception) {
                Log.w(TAG, "Could not parse SimpleResultResponse. Assuming success based on HTTP status.", e)
                true
            }
        } else {
            false
        }
    }

    suspend fun addTimer(
        title: String, sRef: String, startTime: Long, endTime: Long, description: String,
        justPlay: Int, repeated: Int, afterEvent: Int
    ): Boolean {
        val query = buildString {
            append("sRef=${URLEncoder.encode(sRef, "UTF-8")}")
            append("&name=${URLEncoder.encode(title, "UTF-8")}")
            append("&description=${URLEncoder.encode(description, "UTF-8")}")
            append("&begin=$startTime")
            append("&end=$endTime")
            append("&justplay=$justPlay")
            append("&repeated=$repeated")
            append("&afterevent=$afterEvent")
        }
        return executeAction(buildUrl(API_TIMER_ADD, query))
    }

    // --- TEMPORARILY DISABLED ---
    // The following functions are commented out to prevent their use until the deletion bug is fully resolved.
    /*
    suspend fun editTimer(
        originalTimer: Timer, newTitle: String, newSRef: String, newStartTime: Long,
        newEndTime: Long, newDescription: String, justPlay: Int, repeated: Int, afterEvent: Int
    ): Boolean {
        if (!deleteTimer(originalTimer)) {
            Log.e(TAG, "editTimer failed because the original timer could not be deleted.")
            return false
        }
        return addTimer(newTitle, newSRef, newStartTime, newEndTime, newDescription, justPlay, repeated, afterEvent)
    }

    suspend fun deleteTimer(timer: Timer): Boolean {
        val beginTimestamp = timer.beginTimestamp
        val endTimestamp = timer.endTimestamp
        val query: String

        if (timer.sRef.isNullOrBlank()) {
            query = "begin=$beginTimestamp&end=$endTimestamp"
        } else {
            val encodedSRef = URLEncoder.encode(timer.sRef, "UTF-8")
            query = "sRef=$encodedSRef&begin=$beginTimestamp&end=$endTimestamp"
        }

        val url = buildUrl(API_TIMER_DELETE, query)
        return executeAction(url)
    }
    */
    // --- END TEMPORARILY DISABLED ---


    suspend fun getBouquets(): Map<String, String>? {
        val jsonString = executeRequest(buildUrl(API_BOUQUETS))
        return jsonString?.let {
            try {
                gson.fromJson(it, BouquetResponse::class.java).bouquets.associate { b -> b[1] to b[0] }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse bouquets JSON.", e); null
            }
        }
    }

    suspend fun getChannelsInBouquet(bouquetSref: String): Map<String, String>? {
        val url = buildUrl(API_GET_SERVICES, "sRef=${URLEncoder.encode(bouquetSref, "UTF-8")}")
        val jsonString = executeRequest(url)
        return jsonString?.let {
            try {
                gson.fromJson(it, ServiceResponse::class.java).services.associate { s -> s.sName to s.sRef }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse channels JSON.", e); null
            }
        }
    }

    suspend fun getTimerList(): List<Timer>? {
        val jsonString = executeRequest(buildUrl(API_TIMER_LIST))
        return jsonString?.let {
            try {
                gson.fromJson(it, TimerListResponse::class.java).timers
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse timer list JSON.", e); null
            }
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
            if (response.isSuccessful) {
                Log.d(TAG, "Request successful for URL: $url. Response: $responseBody")
                responseBody
            } else {
                Log.e(TAG, "Request failed with code: ${response.code} for URL: $url. Response: $responseBody")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "IOException during request for URL: $url", e); null
        }
    }
}

